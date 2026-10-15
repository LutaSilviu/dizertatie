import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import NavShell from './layout/NavShell'
import AnalyzerPage from './analyzer/AnalyzerPage'
import BenchmarkPage from './benchmark/BenchmarkPage'
import SettingsPage from './settings/SettingsPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<NavShell />}>
          <Route index element={<Navigate to="/analyzer" replace />} />
          <Route path="/analyzer" element={<AnalyzerPage />} />
          <Route path="/benchmark" element={<BenchmarkPage />} />
          <Route path="/benchmark/:runId" element={<BenchmarkPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
