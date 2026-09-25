import { CalendarIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from 'cn'

export function DatePicker({
  id,
  value,
  onSelect,
  placeholder = 'Pick a date',
  disabled,
}: {
  id?: string
  value: Date | undefined
  onSelect: (date: Date | undefined) => void
  placeholder?: string
  disabled?: (date: Date) => boolean
}) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          className={cn('justify-start font-normal', !value && 'text-muted-foreground')}
        >
          <CalendarIcon />
          {value ? value.toLocaleDateString(undefined, { dateStyle: 'medium' }) : placeholder}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar mode="single" selected={value} onSelect={onSelect} disabled={disabled} autoFocus />
      </PopoverContent>
    </Popover>
  )
}
