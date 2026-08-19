import { Outlet, createBrowserRouter } from 'react-router-dom'
import LandingPage from '@/pages/LandingPage'
import NotFoundPage from '@/pages/NotFoundPage'
import LoginPage from '@/pages/LoginPage'
import AdminConsolePage from '@/pages/AdminConsolePage'
import FeedPage from '@/pages/FeedPage'
import CounterPage from '@/pages/CounterPage'
import AppShell from '@/features/app-shell/components/AppShell'
import { AdminSessionProvider } from '@/features/admin/context/AdminSessionContext'

export const router = createBrowserRouter([
  { path: '/', element: <LandingPage /> },
  {
    element: (
      <AdminSessionProvider>
        <Outlet />
      </AdminSessionProvider>
    ),
    children: [
      { path: '/login', element: <LoginPage /> },
      {
        element: <AppShell />,
        children: [
          { path: '/feed', element: <FeedPage /> },
          { path: '/admin/console', element: <AdminConsolePage /> },
          { path: '/admin/counter', element: <CounterPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])
