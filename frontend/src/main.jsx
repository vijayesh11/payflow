import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import PublicInvoice from './pages/PublicInvoice.jsx'
import { ErrorBoundary } from './ui'
import './styles.css'

// Public routes need no login (the hosting rewrites every path to index.html)
const payMatch = window.location.pathname.match(/^\/pay\/([A-Za-z0-9_-]+)\/?$/)

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ErrorBoundary>
      {payMatch ? <PublicInvoice token={payMatch[1]} /> : <App />}
    </ErrorBoundary>
  </React.StrictMode>
)
