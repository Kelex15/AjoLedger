import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { Account, UserAccountType } from '../../api/accounts'
import { ApiError } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import { transfer } from '../../api/transactions'
import { useAuth } from '../../auth/useAuth'
import { accountLabel, formatMoney } from '../../lib/format'
import { useFormErrors } from '../../hooks/useFormErrors'
import { AccountIcon } from '../AccountIcon'
import { AmountInput } from '../AmountInput'
import { ErrorMessage } from '../ErrorMessage'
import { FieldError } from '../FieldError'
import { SuccessPanel } from '../SuccessPanel'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

const DESTINATIONS: { value: UserAccountType; label: string }[] = [
  { value: 'CHECKING', label: 'Checking' },
  { value: 'SAVINGS', label: 'Savings' },
  { value: 'LIEN', label: 'Lien' },
]

function destinationsFor(from: string) {
  return DESTINATIONS.filter((option) => option.value !== from)
}

export function TransferDialog({ fromAccount, children }: { fromAccount: Account; children: ReactNode }) {
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const destinations = destinationsFor(fromAccount.accountType)

  const [open, setOpen] = useState(false)
  const [toAnotherUser, setToAnotherUser] = useState(false)
  const [toUsername, setToUsername] = useState('')
  const [toType, setToType] = useState<UserAccountType>(destinations[0].value)
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const { errors, validate, clearError } = useFormErrors()

  const mutation = useMutation({
    mutationFn: transfer,
    onSuccess: (transaction) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
      queryClient.invalidateQueries({ queryKey: queryKeys.lien })
      queryClient.invalidateQueries({ queryKey: queryKeys.activity })
      setSuccess(
        `${formatMoney(transaction.amount, transaction.currency)} to ${
          toAnotherUser ? `@${toUsername}` : accountLabel(toType)
        }`,
      )
      setAmount('')
    },
  })

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    if (!validate(event.currentTarget)) {
      return
    }
    try {
      await mutation.mutateAsync({
        fromType: fromAccount.accountType as UserAccountType,
        toUsername: toAnotherUser ? toUsername : user!.username,
        // Transfers to another user can only land in their Checking account.
        toType: toAnotherUser ? 'CHECKING' : toType,
        amount: Number(amount),
      })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (!next) {
          setError(null)
          setSuccess(null)
        }
      }}
    >
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{success ? 'Transfer complete' : 'Transfer'}</DialogTitle>
          {!success && (
            <DialogDescription>
              Move money out of {accountLabel(fromAccount.accountType)}, to another of your accounts or to someone
              else.
            </DialogDescription>
          )}
        </DialogHeader>

        {success ? (
          <>
            <SuccessPanel detail={success} />
            <DialogFooter>
              <Button onClick={() => setOpen(false)}>Done</Button>
            </DialogFooter>
          </>
        ) : (
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
          <div className="grid gap-1.5">
            <Label>From</Label>
            <div className="flex items-center gap-2.5 rounded-lg border border-border bg-muted/40 px-2.5 py-2">
              <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                <AccountIcon accountType={fromAccount.accountType} className="size-3.5" />
              </span>
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="text-sm font-medium">{accountLabel(fromAccount.accountType)}</span>
                <span data-numeric className="text-xs text-muted-foreground">
                  ···· {fromAccount.accountNumber.slice(-4)}
                </span>
              </div>
              <span data-numeric className="shrink-0 text-sm text-muted-foreground">
                {formatMoney(fromAccount.balance, fromAccount.currency)}
              </span>
            </div>
          </div>

          {toAnotherUser ? (
            <div className="grid gap-1.5">
              <Label htmlFor="transfer-username">Recipient username</Label>
              <Input
                id="transfer-username"
                name="toUsername"
                value={toUsername}
                onChange={(event) => setToUsername(event.target.value)}
                onInput={() => clearError('toUsername')}
                aria-invalid={Boolean(errors.toUsername)}
                data-required-message="Enter who you're sending to."
                required
              />
              <FieldError message={errors.toUsername} />
              <p className="text-xs text-muted-foreground">Lands in their Checking account.</p>
            </div>
          ) : (
            <div className="grid gap-1.5">
              <Label htmlFor="transfer-to">To</Label>
              {destinations.length === 1 ? (
                <div className="rounded-lg border border-border bg-muted/40 px-2.5 py-2 text-sm font-medium">
                  {destinations[0].label}
                </div>
              ) : (
                <Select value={toType} onValueChange={(value) => setToType(value as UserAccountType)}>
                  <SelectTrigger id="transfer-to">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {destinations.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
          )}

          <div className="flex items-center gap-2">
            <Checkbox
              id="transfer-to-another-user"
              checked={toAnotherUser}
              onCheckedChange={(checked) => setToAnotherUser(checked === true)}
            />
            <Label htmlFor="transfer-to-another-user">Send to another user</Label>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="transfer-amount">Amount</Label>
            <AmountInput
              id="transfer-amount"
              name="amount"
              value={amount}
              onValueChange={(next) => {
                setAmount(next)
                clearError('amount')
              }}
              aria-invalid={Boolean(errors.amount)}
              data-required-message="Enter an amount."
              required
            />
            <FieldError message={errors.amount} />
          </div>

          <ErrorMessage message={error} />

          <DialogFooter showCloseButton>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Transferring…' : 'Transfer'}
            </Button>
          </DialogFooter>
        </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
