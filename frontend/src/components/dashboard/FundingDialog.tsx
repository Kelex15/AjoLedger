import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import {
  deposit,
  withdraw,
  type DepositFields,
  type DepositResult,
  type FundingTransaction,
  type FundingType,
} from '../../api/funding'
import { queryKeys } from '../../api/queryKeys'
import { formatMoney } from '../../lib/format'
import { useFormErrors } from '../../hooks/useFormErrors'
import { AmountInput } from '../AmountInput'
import { ErrorMessage } from '../ErrorMessage'
import { FieldError } from '../FieldError'
import { SuccessPanel } from '../SuccessPanel'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

const COPY = {
  deposit: {
    title: 'Deposit funds',
    description: 'Money lands in your Checking account once the payment clears.',
    submit: 'Deposit',
    pending: 'Depositing…',
    done: 'Deposit complete',
  },
  withdraw: {
    title: 'Withdraw funds',
    description: 'Withdrawals always draw from your Checking account.',
    submit: 'Withdraw',
    pending: 'Withdrawing…',
    done: 'Withdrawal complete',
  },
} as const

export function FundingDialog({ mode, children }: { mode: keyof typeof COPY; children: ReactNode }) {
  const copy = COPY[mode]
  const queryClient = useQueryClient()

  const [open, setOpen] = useState(false)
  const [fundingType, setFundingType] = useState<FundingType>('STRIPE')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const { errors, validate, clearError } = useFormErrors()

  const mutation = useMutation<DepositResult | FundingTransaction, Error, DepositFields>({
    mutationFn: (fields) => (mode === 'deposit' ? deposit(fields) : withdraw(fields)),
    onSuccess: (result) => {
      // A deposit hands back a hosted checkout URL; the money only moves once the user pays there.
      if ('checkoutUrl' in result && result.checkoutUrl) {
        window.location.href = result.checkoutUrl
        return
      }
      queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
      queryClient.invalidateQueries({ queryKey: queryKeys.activity })
      setSuccess(formatMoney(Number(amount), 'USD'))
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
      await mutation.mutateAsync({ fundingType, amount: Number(amount) })
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
          <DialogTitle>{success ? copy.done : copy.title}</DialogTitle>
          {!success && <DialogDescription>{copy.description}</DialogDescription>}
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
            <Label htmlFor={`${mode}-via`}>Payment method</Label>
            <Select value={fundingType} onValueChange={(value) => setFundingType(value as FundingType)}>
              <SelectTrigger id={`${mode}-via`}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="STRIPE">Stripe</SelectItem>
                <SelectItem value="PAYPAL">PayPal</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor={`${mode}-amount`}>Amount</Label>
            <AmountInput
              id={`${mode}-amount`}
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
              {mutation.isPending ? copy.pending : copy.submit}
            </Button>
          </DialogFooter>
        </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
