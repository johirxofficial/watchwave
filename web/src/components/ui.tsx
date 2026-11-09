import { useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react'
import { cn } from '../lib/cn'
import { AlertTriangleIcon, CheckIcon, InfoIcon, LoaderIcon } from './Icons'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
type ButtonSize = 'sm' | 'md' | 'lg'

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-wave-cyan text-ink-950 hover:bg-cyan-300 shadow-[0_14px_40px_-18px_rgba(34,211,238,.85)] focus-visible:ring-wave-cyan/70',
  secondary:
    'border border-white/12 bg-white/[0.06] text-slate-100 hover:border-white/20 hover:bg-white/[0.1] focus-visible:ring-white/30',
  ghost: 'text-slate-300 hover:bg-white/[0.07] hover:text-white focus-visible:ring-white/25',
  danger:
    'border border-wave-red/30 bg-wave-red/10 text-wave-red hover:bg-wave-red/16 focus-visible:ring-wave-red/50',
}

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'h-9 px-3.5 text-[13px] gap-1.5',
  md: 'h-11 px-5 text-sm gap-2',
  lg: 'h-12 px-6 text-[15px] gap-2.5',
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
  children: ReactNode
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  className,
  children,
  disabled,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={cn(
        'inline-flex select-none items-center justify-center rounded-xl font-medium outline-none transition duration-150 focus-visible:ring-2 disabled:cursor-not-allowed disabled:opacity-50',
        variantClasses[variant],
        sizeClasses[size],
        className,
      )}
      {...props}
    >
      {loading ? <LoaderIcon size={16} className="animate-spin" /> : null}
      {children}
    </button>
  )
}

export function Panel({
  children,
  className,
  accent = false,
}: {
  children: ReactNode
  className?: string
  accent?: boolean
}) {
  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-2xl border border-white/[0.08] bg-ink-850/80 shadow-panel backdrop-blur',
        accent && 'border-wave-cyan/20 shadow-glow',
        className,
      )}
    >
      {children}
    </div>
  )
}

export function Pill({
  children,
  tone = 'neutral',
  className,
  title,
}: {
  children: ReactNode
  tone?: 'neutral' | 'cyan' | 'green' | 'amber' | 'red'
  className?: string
  title?: string
}) {
  const tones = {
    neutral: 'border-white/10 bg-white/[0.05] text-slate-300',
    cyan: 'border-wave-cyan/25 bg-wave-cyan/10 text-wave-cyan',
    green: 'border-wave-green/25 bg-wave-green/10 text-wave-green',
    amber: 'border-wave-amber/25 bg-wave-amber/10 text-wave-amber',
    red: 'border-wave-red/25 bg-wave-red/10 text-wave-red',
  }
  return (
    <span
      title={title}
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-medium uppercase tracking-[0.12em]',
        tones[tone],
        className,
      )}
    >
      {children}
    </span>
  )
}

export function LiveDot({ tone = 'green' }: { tone?: 'green' | 'amber' | 'cyan' | 'red' }) {
  const colors = {
    green: 'bg-wave-green',
    amber: 'bg-wave-amber',
    cyan: 'bg-wave-cyan',
    red: 'bg-wave-red',
  }
  return (
    <span className="relative flex size-2">
      <span className={cn('absolute inline-flex size-2 animate-ping rounded-full opacity-60', colors[tone])} />
      <span className={cn('relative inline-flex size-2 rounded-full', colors[tone])} />
    </span>
  )
}

export function TextField({
  label,
  hint,
  error,
  className,
  id,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label?: string; hint?: string; error?: string }) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  return (
    <label htmlFor={inputId} className="block">
      {label ? (
        <span className="mb-1.5 flex items-center justify-between gap-3 text-[12px] font-medium uppercase tracking-[0.12em] text-wave-muted">
          {label}
          {hint ? <span className="normal-case tracking-normal text-wave-muted/70">{hint}</span> : null}
        </span>
      ) : null}
      <input
        id={inputId}
        className={cn(
          'h-12 w-full rounded-xl border border-white/10 bg-ink-900/80 px-4 text-[15px] text-white placeholder:text-wave-muted/50 outline-none transition focus:border-wave-cyan/50 focus:bg-ink-900 focus:ring-4 focus:ring-wave-cyan/10',
          error && 'border-wave-red/50 focus:border-wave-red/60 focus:ring-wave-red/10',
          className,
        )}
        {...props}
      />
      {error ? <span className="mt-1.5 block text-xs text-wave-red">{error}</span> : null}
    </label>
  )
}

export function Avatar({
  name,
  host = false,
  size = 'md',
}: {
  name: string
  host?: boolean
  size?: 'sm' | 'md'
}) {
  const letters = name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
  const palette = ['from-cyan-400/30 to-teal-500/10 text-cyan-100', 'from-amber-300/25 to-orange-500/10 text-amber-100', 'from-fuchsia-400/25 to-purple-500/10 text-fuchsia-100', 'from-emerald-400/25 to-lime-500/10 text-emerald-100']
  let hash = 0
  for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return (
    <span
      className={cn(
        'relative grid shrink-0 place-items-center rounded-full border border-white/10 bg-gradient-to-br font-semibold',
        palette[hash % palette.length],
        size === 'sm' ? 'size-7 text-[10px]' : 'size-9 text-xs',
      )}
      aria-hidden="true"
    >
      {letters || '?'}
      {host ? <span className="absolute -right-1 -top-1 size-2.5 rounded-full border-2 border-ink-850 bg-wave-amber" /> : null}
    </span>
  )
}

export function Banner({
  kind = 'info',
  title,
  children,
  action,
}: {
  kind?: 'info' | 'warn' | 'error' | 'success'
  title?: string
  children: ReactNode
  action?: ReactNode
}) {
  const tones = {
    info: 'border-wave-cyan/25 bg-wave-cyan/[0.07] text-wave-cyan',
    warn: 'border-wave-amber/30 bg-wave-amber/[0.08] text-wave-amber',
    error: 'border-wave-red/30 bg-wave-red/[0.08] text-wave-red',
    success: 'border-wave-green/30 bg-wave-green/[0.08] text-wave-green',
  }
  const Icon = kind === 'error' || kind === 'warn' ? AlertTriangleIcon : kind === 'success' ? CheckIcon : InfoIcon
  return (
    <div className={cn('flex items-start gap-3 rounded-xl border px-4 py-3 text-sm', tones[kind])}>
      <Icon size={18} className="mt-0.5 shrink-0" />
      <div className="min-w-0 flex-1">
        {title ? <p className="font-medium text-inherit">{title}</p> : null}
        <div className={cn('text-[13px] leading-relaxed', title ? 'mt-0.5 opacity-90' : '')}>{children}</div>
      </div>
      {action}
    </div>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        'animate-shimmer rounded-xl bg-[linear-gradient(90deg,rgba(255,255,255,.04),rgba(255,255,255,.09),rgba(255,255,255,.04))] bg-[length:200%_100%]',
        className,
      )}
    />
  )
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <span className="inline-flex items-center gap-2 text-sm text-wave-muted" role="status">
      <LoaderIcon size={16} className="animate-spin text-wave-cyan" />
      {label}
    </span>
  )
}
