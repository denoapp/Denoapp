const fs = require('fs')

const file = 'src/App.jsx'
let s = fs.readFileSync(file, 'utf8')

if (!s.includes("const [loggedIn, setLoggedIn]")) {
  s = s.replace(
    "  const [active, setActive] = useState('Dashboard')",
    "  const [loggedIn, setLoggedIn] = useState(false)\n  const [active, setActive] = useState('Dashboard')"
  )
}

const loginBlock = `
  if (!loggedIn) {
    return (
      <div className="admin-login-page">
        <div className="admin-login-card">
          <div className="admin-login-brand">
            <div className="brand-mark">D</div>
            <div>
              <strong>DENO</strong>
              <span>Admin Panel</span>
            </div>
          </div>

          <div className="admin-login-heading">
            <p className="eyebrow">SECURE ADMIN ACCESS</p>
            <h1>Admin Login</h1>
            <p>Sign in to manage the DENO administration panel.</p>
          </div>

          <form
            className="admin-login-form"
            onSubmit={(e) => {
              e.preventDefault()
              setLoggedIn(true)
            }}
          >
            <label>
              Admin Email
              <input type="email" placeholder="admin@deno.app" required />
            </label>

            <label>
              Password
              <input type="password" placeholder="Enter password" required />
            </label>

            <button type="submit" className="login-button">
              Sign In
            </button>
          </form>

          <div className="login-security-note">
            <strong>Backend authentication required</strong>
            <span>Real password verification and admin permissions will be enforced by the backend.</span>
          </div>
        </div>
      </div>
    )
  }

`

if (!s.includes('SECURE ADMIN ACCESS')) {
  const marker = "  const openPage = (page) => {"
  if (!s.includes(marker)) throw new Error('openPage marker not found')
  s = s.replace(marker, loginBlock + marker)
}

fs.writeFileSync(file, s)
console.log('Admin Login frontend added successfully.')
