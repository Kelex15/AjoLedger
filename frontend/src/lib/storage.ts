export const storageKeys = {
  theme: 'ajoledger-theme',
} as const

export function getStoredItem(key: string) {
  return localStorage.getItem(key)
}

export function setStoredItem(key: string, value: string) {
  localStorage.setItem(key, value)
}
