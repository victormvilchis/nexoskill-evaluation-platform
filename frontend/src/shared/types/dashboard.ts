export interface DashboardModule {
  code: string
  name: string
  description: string
  enabled: boolean
}

export interface WelcomeDashboard {
  panelType: 'ADMIN' | 'USER'
  title: string
  message: string
  generatedAt: string
  modules: DashboardModule[]
}
