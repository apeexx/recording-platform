const path = require('node:path')

const appRoot = path.resolve(__dirname, '..')

module.exports = {
  apps: [
    {
      name: 'recording-platform-backend',
      cwd: appRoot,
      script: '/usr/bin/java',
      args: [
        '-jar',
        path.join(
          appRoot,
          'backend',
          'target',
          'recording-platform-backend-0.0.1-SNAPSHOT.jar'
        )
      ],
      interpreter: 'none',
      exec_mode: 'fork',
      instances: 1,
      autorestart: true,
      watch: false,
      restart_delay: 5000,
      min_uptime: 10000,
      max_restarts: 10,
      kill_timeout: 15000,
      time: true,
      out_file: '/var/log/recording-platform/backend-out.log',
      error_file: '/var/log/recording-platform/backend-error.log',
      merge_logs: true,
      env: {
        SERVER_ADDRESS: '127.0.0.1',
        SERVER_PORT: '8080'
      }
    }
  ]
}
