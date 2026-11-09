import type { SVGProps } from 'react'

export interface IconProps extends SVGProps<SVGSVGElement> {
  size?: number
}

function Svg({ size = 20, children, ...props }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      {children}
    </svg>
  )
}

export function PlayIcon({ size = 20, ...props }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false" {...props}>
      <path d="M8 5.14v13.72a1 1 0 0 0 1.52.85l11.14-6.86a1 1 0 0 0 0-1.7L9.52 4.29A1 1 0 0 0 8 5.14Z" />
    </svg>
  )
}

export function PauseIcon({ size = 20, ...props }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false" {...props}>
      <rect x="6" y="4" width="4.2" height="16" rx="1.2" />
      <rect x="13.8" y="4" width="4.2" height="16" rx="1.2" />
    </svg>
  )
}

export function SkipBackIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M11 5 4 12l7 7" />
      <path d="M20 5v14" />
    </Svg>
  )
}

export function SkipForwardIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m13 5 7 7-7 7" />
      <path d="M4 5v14" />
    </Svg>
  )
}

export function SendIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m22 2-7 20-4-9-9-4Z" />
      <path d="M22 2 11 13" />
    </Svg>
  )
}

export function ChatIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4Z" />
    </Svg>
  )
}

export function VolumeIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M11 5 6.5 9H3v6h3.5L11 19V5Z" />
      <path d="M15 9a4 4 0 0 1 0 6" />
      <path d="M18 6.5a8 8 0 0 1 0 11" />
    </Svg>
  )
}

export function VolumeMuteIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M11 5 6.5 9H3v6h3.5L11 19V5Z" />
      <path d="m16 9 5 6" />
      <path d="m21 9-5 6" />
    </Svg>
  )
}

export function MaximizeIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M8 3H3v5" />
      <path d="M16 3h5v5" />
      <path d="M21 16v5h-5" />
      <path d="M3 16v5h5" />
    </Svg>
  )
}

export function MinimizeIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M3 8h5V3" />
      <path d="M21 8h-5V3" />
      <path d="M16 21v-5h5" />
      <path d="M8 21v-5H3" />
    </Svg>
  )
}

export function PipIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="2.5" y="4.5" width="19" height="15" rx="2.5" />
      <rect x="12" y="11.5" width="7" height="5.5" rx="1.2" />
    </Svg>
  )
}

export function GaugeIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M4 18a8 8 0 1 1 16 0" />
      <path d="m12 14 3.5-4.5" />
      <circle cx="12" cy="15" r="1.4" fill="currentColor" stroke="none" />
    </Svg>
  )
}

export function UsersIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.5 19.5a5.5 5.5 0 0 1 11 0" />
      <path d="M16 5.4a3.2 3.2 0 0 1 0 5.2" />
      <path d="M17.5 14.4a5.5 5.5 0 0 1 3 5.1" />
    </Svg>
  )
}

export function CopyIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="9" y="9" width="11" height="11" rx="2" />
      <path d="M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1" />
    </Svg>
  )
}

export function CheckIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m4.5 12.5 5 5 10-11" />
    </Svg>
  )
}

export function LinkIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M10.5 13.5a4 4 0 0 0 5.7 0l2.8-2.8a4 4 0 0 0-5.7-5.7l-1.6 1.6" />
      <path d="M13.5 10.5a4 4 0 0 0-5.7 0l-2.8 2.8a4 4 0 0 0 5.7 5.7l1.6-1.6" />
    </Svg>
  )
}

export function ArrowRightIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M4 12h15" />
      <path d="m13 6 6 6-6 6" />
    </Svg>
  )
}

export function ArrowLeftIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M20 12H5" />
      <path d="m11 6-6 6 6 6" />
    </Svg>
  )
}

export function LockIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="5" y="10" width="14" height="10" rx="2" />
      <path d="M8 10V7.5a4 4 0 0 1 8 0V10" />
    </Svg>
  )
}

export function UnlockIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="5" y="10" width="14" height="10" rx="2" />
      <path d="M8 10V7.5a4 4 0 0 1 7.5-2" />
    </Svg>
  )
}

export function CrownIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m3.5 7 4.5 3 4-5 4 5 4.5-3-1.7 11.5H5.2L3.5 7Z" />
      <path d="M5.2 18.5h13.6" />
    </Svg>
  )
}

export function WifiIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M3 9a14 14 0 0 1 18 0" />
      <path d="M6.5 12.5a9 9 0 0 1 11 0" />
      <path d="M9.5 16a4.5 4.5 0 0 1 5 0" />
      <circle cx="12" cy="19.5" r="1" fill="currentColor" stroke="none" />
    </Svg>
  )
}

export function WifiOffIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M3 3 21 21" />
      <path d="M3 9a14 14 0 0 1 5-2.8" />
      <path d="M15.8 6.6A14 14 0 0 1 21 9" />
      <path d="M6.5 12.5a9 9 0 0 1 4-2.1" />
      <path d="M16.8 11.9a9 9 0 0 1 .7.6" />
      <path d="M9.5 16a4.5 4.5 0 0 1 5 0" />
      <circle cx="12" cy="19.5" r="1" fill="currentColor" stroke="none" />
    </Svg>
  )
}

export function RefreshIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M20 6v5h-5" />
      <path d="M4 18v-5h5" />
      <path d="M6.1 9a7 7 0 0 1 11.2-2.3L20 11" />
      <path d="M17.9 15a7 7 0 0 1-11.2 2.3L4 13" />
    </Svg>
  )
}

export function LogOutIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M9 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h4" />
      <path d="M15 8l4 4-4 4" />
      <path d="M19 12H9" />
    </Svg>
  )
}

export function AlertTriangleIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 3.5 22 20H2L12 3.5Z" />
      <path d="M12 9v5" />
      <circle cx="12" cy="17.2" r="0.9" fill="currentColor" stroke="none" />
    </Svg>
  )
}

export function InfoIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5" />
      <circle cx="12" cy="7.8" r="0.9" fill="currentColor" stroke="none" />
    </Svg>
  )
}

export function CloseIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="m6 6 12 12" />
      <path d="m18 6-12 12" />
    </Svg>
  )
}

export function ShareIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="6" cy="12" r="2.4" />
      <circle cx="18" cy="6" r="2.4" />
      <circle cx="18" cy="18" r="2.4" />
      <path d="m8.2 10.9 7.6-3.8" />
      <path d="m8.2 13.1 7.6 3.8" />
    </Svg>
  )
}

export function PhoneIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="6.5" y="2.5" width="11" height="19" rx="2.5" />
      <path d="M10.5 18.5h3" />
    </Svg>
  )
}

export function MonitorIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="2.5" y="4" width="19" height="12.5" rx="2" />
      <path d="M8 20.5h8" />
      <path d="M12 16.5v4" />
    </Svg>
  )
}

export function ShieldIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M12 3 20 6v6c0 4.6-3.2 7.7-8 9-4.8-1.3-8-4.4-8-9V6l8-3Z" />
      <path d="m8.5 12 2.4 2.4 4.6-5" />
    </Svg>
  )
}

export function ZapIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M13 2 4.5 13.5H11L9.5 22 19 9.5h-6.5L13 2Z" />
    </Svg>
  )
}

export function LoaderIcon({ className, ...props }: IconProps) {
  return (
    <Svg className={className} {...props}>
      <path d="M12 3a9 9 0 1 0 9 9" />
    </Svg>
  )
}

export function WaveIcon({ size = 24, ...props }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none" aria-hidden="true" focusable="false" {...props}>
      <path
        d="M2 18c3-5.2 6-5.2 9 0s6 5.2 9 0 6-5.2 9 0"
        stroke="currentColor"
        strokeWidth="2.6"
        strokeLinecap="round"
      />
      <path
        d="M4 24c2.4-3.4 4.8-3.4 7.2 0s4.8 3.4 7.2 0 4.8-3.4 7.2 0"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        opacity=".5"
      />
    </svg>
  )
}
