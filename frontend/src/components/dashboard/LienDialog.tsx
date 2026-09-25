import { useState, type ReactNode } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import { lienHold, lienWithdraw, type LedgerTransaction } from '../../api/transactions'
import { formatMoney } from '../../lib/format'
import { AmountInput } from '../AmountInput'
import { ErrorMessage } from '../ErrorMessage'
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

export function LienDialog({
  withdrawable,
  currency,
  children,
}: {
  withdrawable: number
  currency: string
  children: ReactNode
}) {
  const queryClient = useQueryClient()

  const [open, setOpen] = useState(false)
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<{ title: string; detail: string } | null>(null)

  function settle(title: string, transaction: LedgerTransaction) {
    queryClient.invalidateQueries({ queryKey: queryKeys.lien })
    queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
    queryClient.invalidateQueries({ queryKey: queryKeys.activity })
    setSuccess({ title, detail: formatMoney(transaction.amount, transaction.currency) })
    setAmount('')
  }

  const holdMutation = useMutation({
    mutationFn: lienHold,
    onSuccess: (transaction) => settle('Added to lien', transaction),
  })
  const withdrawMutation = useMutation({
    mutationFn: lienWithdraw,
    onSuccess: (transaction) => settle('Withdrawn from lien', transaction),
  })

  async function run(mutation: typeof holdMutation) {
    setError(null)
    try {
      await mutation.mutateAsync(Number(amount))
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
          <DialogTitle>{success ? success.title : 'Lien'}</DialogTitle>
          {!success && (
            <DialogDescription>
              Money here backs your circle commitments. Only{' '}
              <span data-numeric>{formatMoney(withdrawable, currency)}</span> is free to withdraw right now.
            </DialogDescription>
          )}
        </DialogHeader>

        {success ? (
          <>
            <SuccessPanel detail={success.detail} />
            <DialogFooter>
              <Button onClick={() => setOpen(false)}>Done</Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <div className="grid gap-1.5">
              <Label htmlFor="lien-amount">Amount</Label>
              <AmountInput id="lien-amount" value={amount} onValueChange={setAmount} />
            </div>

            <ErrorMessage message={error} />

            <DialogFooter>
              <Button
                variant="outline"
                onClick={() => run(withdrawMutation)}
                disabled={withdrawMutation.isPending || !amount}
              >
                {withdrawMutation.isPending ? 'Withdrawing…' : 'Withdraw'}
              </Button>
              <Button onClick={() => run(holdMutation)} disabled={holdMutation.isPending || !amount}>
                {holdMutation.isPending ? 'Adding…' : 'Add to lien'}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
