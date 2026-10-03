const fs = require('fs')

const file = 'src/App.jsx'
let s = fs.readFileSync(file, 'utf8')

if (!s.includes("Admin Profile")) {
  const insertBefore = "  const openPage = (page) => {"

  const block = `
  const renderAdminProfile = () => (
    <main className="page-content">
      <div className="page-header">
        <div>
          <p className="eyebrow">ADMIN ACCOUNT</p>
          <h1>Admin Profile</h1>
          <p>Manage your administrator profile and account security.</p>
        </div>
      </div>

      <div className="profile-grid">
        <section className="admin-panel-card">
          <div className="profile-avatar">D</div>
          <h2>Administrator</h2>
          <p className="muted">Super Admin</p>
          <span className="status-badge status-active">ACTIVE</span>
        </section>

        <section className="admin-panel-card">
          <h2>Profile Information</h2>

          <div className="profile-form">
            <label>
              Full Name
              <input type="text" defaultValue="Administrator" />
            </label>

            <label>
              Admin Email
              <input type="email" defaultValue="admin@deno.app" />
            </label>

            <label>
              Role
              <input type="text" value="Super Admin" readOnly />
            </label>

            <button className="primary-action" type="button">
              Save Profile
            </button>
          </div>
        </section>

        <section className="admin-panel-card">
          <h2>Security</h2>

          <div className="security-row">
            <div>
              <strong>Password</strong>
              <span>Change your administrator password.</span>
            </div>
            <button className="secondary-action" type="button">Change Password</button>
          </div>

          <div className="security-row">
            <div>
              <strong>Two-Factor Authentication</strong>
              <span>Protect administrator access with 2FA.</span>
            </div>
            <span className="status-badge status-pending">NOT CONNECTED</span>
          </div>

          <div className="security-row">
            <div>
              <strong>Active Sessions</strong>
              <span>Review and revoke administrator sessions.</span>
            </div>
            <button className="secondary-action" type="button">View Sessions</button>
          </div>
        </section>

        <section className="admin-panel-card">
          <h2>Backend Security</h2>
          <p className="muted">
            Password verification, admin roles, 2FA, session control and permissions
            will be enforced by the backend.
          </p>
          <div className="security-note">
            Real administrator credentials will never be stored in the frontend.
          </div>
        </section>
      </div>
    </main>
  )

`

  if (!s.includes(insertBefore)) {
    throw new Error('Profile insertion marker not found')
  }

  s = s.replace(insertBefore, block + insertBefore)

  const navMarker = "    if (active === 'Dashboard')"
  if (!s.includes(navMarker)) {
    throw new Error('Dashboard navigation marker not found')
  }

  s = s.replace(
    navMarker,
    "    if (active === 'Admin Profile') return renderAdminProfile()\\n" +
    navMarker
  )

  const menuMarker = "  ['Dashboard', '▦'],"
  s = s.replace(
    menuMarker,
    menuMarker + "\\n  ['Admin Profile', '●'],"
  )
}

fs.writeFileSync(file, s)
console.log('Admin Profile and Security frontend added successfully.')
