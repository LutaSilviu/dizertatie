import { Link } from 'react-router-dom'
import BenchmarkForm from './BenchmarkForm'

function BenchmarkPage() {
  return (
    <div className="app-page">
      <Link to="/benchmark" style={{ color: '#1a7f64', textDecoration: 'none', fontSize: '0.9rem' }}>
        ← All benchmark runs
      </Link>
      <h1>New Benchmark</h1>
      <BenchmarkForm />
    </div>
  )
}

export default BenchmarkPage
