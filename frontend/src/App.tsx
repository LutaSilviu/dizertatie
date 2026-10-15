import { BrowserRouter, Navigate, Route, Routes, useParams } from 'react-router-dom'
import NavShell from './layout/NavShell'
import AnalyzerPage from './analyzer/AnalyzerPage'
import BenchmarkPage from './benchmark/BenchmarkPage'
import SettingsPage from './settings/SettingsPage'
import HistoryPage from './history/HistoryPage'

function ReportPage() {
  const { scanId } = useParams<{ scanId: string }>()
  return (
    <div style={{ padding: '1.5rem' }}>
      <h1>Report</h1>
      <p>Scan ID: {scanId}</p>
    </div>
  )
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<NavShell />}>
          <Route index element={<Navigate to="/analyzer" replace />} />
          <Route path="/analyzer" element={<AnalyzerPage />} />
          <Route path="/report/:scanId" element={<ReportPage />} />
          <Route path="/benchmark" element={<BenchmarkPage />} />
          <Route path="/benchmark/:runId" element={<BenchmarkPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/history" element={<HistoryPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
