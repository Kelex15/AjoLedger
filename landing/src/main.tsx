import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { ProductPage } from './ProductPage'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ProductPage />
  </StrictMode>,
)
