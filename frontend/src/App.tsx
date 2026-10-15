import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import NavShell from './layout/NavShell'
import AnalyzerPage from './analyzer/AnalyzerPage'
import BenchmarkPage from './benchmark/BenchmarkPage'
import BenchmarkResultPage from './benchmark/BenchmarkResultPage'
import BenchmarkRunsListPage from './benchmark/BenchmarkRunsListPage'
import SettingsPage from './settings/SettingsPage'
import ReportPage from './report/ReportPage'
import HistoryPage from './history/HistoryPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<NavShell />}>
          <Route index element={<Navigate to="/analyzer" replace />} />
          <Route path="/analyzer" element={<AnalyzerPage />} />
          <Route path="/report/:scanId" element={<ReportPage />} />
          <Route path="/benchmark" element={<BenchmarkRunsListPage />} />
          <Route path="/benchmark/new" element={<BenchmarkPage />} />
          <Route path="/benchmark/:runId" element={<BenchmarkResultPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/history" element={<HistoryPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
