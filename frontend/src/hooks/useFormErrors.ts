import { useState } from 'react'

type Validatable = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement

function isValidatable(element: Element): element is Validatable {
  return (
    element instanceof HTMLInputElement ||
    element instanceof HTMLSelectElement ||
    element instanceof HTMLTextAreaElement
  )
}

function messageFor(element: Validatable): string | undefined {
  const { validity } = element
  if (validity.valueMissing) {
    return element.dataset.requiredMessage ?? 'This field is required.'
  }
  if (validity.typeMismatch && element instanceof HTMLInputElement && element.type === 'email') {
    return 'Enter a valid email address.'
  }
  if (!validity.valid) {
    return element.validationMessage
  }
  // Sanitized numeric fields are type="text", so the browser never enforces their minimum.
  const min = element.dataset.min
  if (min && element.value !== '' && Number(element.value) < Number(min)) {
    return `Enter ${min} or more.`
  }
  return undefined
}

/** Requires `noValidate` on the form: constraints still drive validation, the failures just
 * render inline instead of as browser bubbles. */
export function useFormErrors() {
  const [errors, setErrors] = useState<Record<string, string>>({})

  /** `extra` covers fields the browser can't see, such as a date picker that isn't an input. */
  function validate(form: HTMLFormElement, extra: Record<string, string | null> = {}) {
    const next: Record<string, string> = {}
    for (const element of Array.from(form.elements)) {
      if (!isValidatable(element) || !element.name) {
        continue
      }
      const message = messageFor(element)
      if (message) {
        next[element.name] = message
      }
    }
    for (const [name, message] of Object.entries(extra)) {
      if (message) {
        next[name] = message
      }
    }
    setErrors(next)
    return Object.keys(next).length === 0
  }

  function clearError(name: string) {
    setErrors((current) => {
      if (!current[name]) {
        return current
      }
      const rest = { ...current }
      delete rest[name]
      return rest
    })
  }

  return { errors, validate, clearError }
}
