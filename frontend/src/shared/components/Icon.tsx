import type { ReactNode, SVGProps } from 'react'

export type IconName =
  | 'menu'
  | 'close'
  | 'home'
  | 'users'
  | 'questions'
  | 'categories'
  | 'clipboard'
  | 'results'
  | 'profile'
  | 'lock'
  | 'logout'
  | 'chevronDown'
  | 'chevronRight'
  | 'plus'
  | 'edit'
  | 'copy'
  | 'archive'
  | 'search'
  | 'check'
  | 'info'
  | 'warning'
  | 'error'
  | 'image'
  | 'collections'
  | 'code'

interface IconProps extends SVGProps<SVGSVGElement> {
  name: IconName
  size?: number
}

const paths: Record<IconName, ReactNode> = {
  menu: <><path d="M4 7h16M4 12h16M4 17h16" /></>,
  close: <><path d="m6 6 12 12M18 6 6 18" /></>,
  home: <><path d="m3 11 9-8 9 8" /><path d="M5 10v10h14V10M9 20v-6h6v6" /></>,
  users: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /></>,
  questions: <><path d="M4 5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z" /><path d="M9.5 9a2.5 2.5 0 1 1 3.4 2.33c-.55.24-.9.74-.9 1.34V13M12 17h.01" /></>,
  categories: <><path d="M3 6h7l2 2h9v11H3z" /><path d="M3 6V4h7l2 2" /></>,
  clipboard: <><rect x="5" y="4" width="14" height="17" rx="2" /><path d="M9 4V2h6v2M9 9h6M9 13h6M9 17h4" /></>,
  results: <><path d="M4 19V9M10 19V5M16 19v-7M22 19H2" /></>,
  profile: <><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></>,
  lock: <><rect x="4" y="10" width="16" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3" /></>,
  logout: <><path d="M10 17l5-5-5-5M15 12H3" /><path d="M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5" /></>,
  chevronDown: <><path d="m6 9 6 6 6-6" /></>,
  chevronRight: <><path d="m9 6 6 6-6 6" /></>,
  plus: <><path d="M12 5v14M5 12h14" /></>,
  edit: <><path d="M12 20h9" /><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L8 18l-4 1 1-4z" /></>,
  copy: <><rect x="9" y="9" width="13" height="13" rx="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" /></>,
  archive: <><path d="M3 6h18M5 6v14h14V6M9 10h6" /><path d="M4 3h16v3H4z" /></>,
  search: <><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></>,
  check: <><path d="m5 12 4 4L19 6" /></>,
  info: <><circle cx="12" cy="12" r="10" /><path d="M12 16v-4M12 8h.01" /></>,
  warning: <><path d="M10.3 3.8 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.8a2 2 0 0 0-3.4 0z" /><path d="M12 9v4M12 17h.01" /></>,
  error: <><circle cx="12" cy="12" r="10" /><path d="m15 9-6 6M9 9l6 6" /></>,
  image: <><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9" r="1.5"/><path d="m21 15-5-5L5 20"/></>,
  collections: <><rect x="4" y="4" width="16" height="6" rx="2"/><rect x="4" y="14" width="16" height="6" rx="2"/></>,
  code: <><path d="m8 9-4 3 4 3M16 9l4 3-4 3M14 5l-4 14"/></>
}

export function Icon({ name, size = 18, ...props }: IconProps) {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      height={size}
      viewBox="0 0 24 24"
      width={size}
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={1.8}
      {...props}
    >
      {paths[name]}
    </svg>
  )
}
