import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { EngineeringPage } from './EngineeringPage'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <EngineeringPage />
  </StrictMode>,
)
