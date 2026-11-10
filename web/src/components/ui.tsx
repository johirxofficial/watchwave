import { useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react'
import { cn } from '../lib/cn'
import { AlertTriangleIcon, CheckIcon, InfoIcon, LoaderIcon } from './Icons'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'soft'
type ButtonSize = 'sm' | 'md' | 'lg' | 'icon'

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-wave-cyan text-ink-950 hover:brightness-110 shadow-[0_16px_44px_-22px_rgb(var(--wv-accent)/.9)] focus-visible:ring-wave-cyan/70',
  secondary:
    'border border-white/12 bg-white/[0.06] text-slate-100 hover:border-white/20 hover:bg-white/[0.1] focus-visible:ring-white/30',
  soft:
    'border border-wave-cyan/25 bg-wave-cyan/10 text-wave-cyan hover:bg-wave-cyan/16 focus-visible:ring-wave-cyan/50',
  ghost: 'text-slate-300 hover:bg-white/[0.07] hover:text-white focus-visible:ring-white/25',
  danger:
    'border border-wave-red/30 bg-wave-red/10 text-wave-red hover:bg-wave-red/16 focus-visible:ring-wave-red/50',
}

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'h-9 px-3.5 text-[13px] gap-1.5',
  md: 'h-11 px-5 text-sm gap-2',
  lg: 'h-12 px-6 text-[15px] gap-2.5',
  icon: 'size-10 p-0',
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
  children?: ReactNode
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
        'app-card',
        accent && 'border-wave-cyan/25 shadow-glow',
        className,
      )}
    >
      {children}
    </div>
  )
}

export function Card({
  children,
  className,
  interactive = false,
  accent = false,
}: {
  children: ReactNode
  className?: string
  interactive?: boolean
  accent?: boolean
}) {
  return (
    <div
      className={cn(
        interactive ? 'app-card-interactive' : 'app-card',
        accent && 'border-wave-cyan/20',
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
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.12em]',
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
  leading,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & {
  label?: string
  hint?: string
  error?: string
  leading?: ReactNode
}) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  return (
    <label htmlFor={inputId} className="block">
      {label ? (
        <span className="mb-1.5 flex items-center justify-between gap-3 text-[11px] font-semibold uppercase tracking-[0.14em] text-wave-muted">
          {label}
          {hint ? <span className="normal-case tracking-normal text-wave-muted/70">{hint}</span> : null}
        </span>
      ) : null}
      <span className="relative block">
        {leading ? (
          <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-wave-muted">
            {leading}
          </span>
        ) : null}
        <input
          id={inputId}
          className={cn(
            'h-12 w-full rounded-xl border border-white/10 bg-ink-900/80 px-4 text-[15px] text-white placeholder:text-wave-muted/45 outline-none transition focus:border-wave-cyan/50 focus:bg-ink-900 focus:ring-4 focus:ring-wave-cyan/10',
            leading ? 'pl-11' : undefined,
            error && 'border-wave-red/50 focus:border-wave-red/60 focus:ring-wave-red/10',
            className,
          )}
          {...props}
        />
      </span>
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
  size?: 'xs' | 'sm' | 'md' | 'lg'
}) {
  const letters = name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
  const palette = [
    'from-sky-400/30 to-blue-600/10 text-sky-100',
    'from-amber-300/25 to-orange-500/10 text-amber-100',
    'from-fuchsia-400/25 to-purple-500/10 text-fuchsia-100',
    'from-emerald-400/25 to-lime-500/10 text-emerald-100',
    'from-rose-400/25 to-red-500/10 text-rose-100',
  ]
  let hash = 0
  for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return (
    <span
      className={cn(
        'relative grid shrink-0 place-items-center rounded-full border border-white/10 bg-gradient-to-br font-semibold',
        palette[hash % palette.length],
        size === 'xs' && 'size-6 text-[9px]',
        size === 'sm' && 'size-7 text-[10px]',
        size === 'md' && 'size-9 text-xs',
        size === 'lg' && 'size-12 text-sm',
      )}
      aria-hidden="true"
    >
      {letters || '?'}
      {host ? (
        <span className="absolute -right-0.5 -top-0.5 size-2.5 rounded-full border-2 border-ink-850 bg-wave-amber" />
      ) : null}
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

export function Toggle({
  checked,
  onChange,
  label,
  description,
  disabled = false,
}: {
  checked: boolean
  onChange: (value: boolean) => void
  label: string
  description?: string
  disabled?: boolean
}) {
  const id = useId()
  return (
    <div className="flex items-start gap-4 py-3.5">
      <div className="min-w-0 flex-1">
        <label htmlFor={id} className="block cursor-pointer text-sm font-medium text-white">
          {label}
        </label>
        {description ? <p className="mt-1 text-[12px] leading-relaxed text-wave-muted">{description}</p> : null}
      </div>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={cn(
          'relative mt-0.5 h-6 w-11 shrink-0 rounded-full border outline-none transition focus-visible:ring-2 focus-visible:ring-wave-cyan/60 disabled:opacity-40',
          checked ? 'border-wave-cyan/40 bg-wave-cyan/80' : 'border-white/15 bg-white/10',
        )}
      >
        <span
          className={cn(
            'absolute left-0 top-0.5 size-4.5 rounded-full bg-white shadow transition-transform',
            checked ? 'translate-x-[22px]' : 'translate-x-0.5',
          )}
          style={{ width: 18, height: 18 }}
        />
      </button>
    </div>
  )
}

export function Segmented<T extends string | number>({
  value,
  options,
  onChange,
  label,
  className,
}: {
  value: T
  options: Array<{ value: T; label: ReactNode }>
  onChange: (value: T) => void
  label: string
  className?: string
}) {
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={cn('inline-flex flex-wrap gap-1 rounded-xl border border-white/10 bg-black/20 p-1', className)}
    >
      {options.map((option) => (
        <button
          key={String(option.value)}
          type="button"
          role="radio"
          aria-checked={option.value === value}
          onClick={() => onChange(option.value)}
          className={cn(
            'rounded-lg px-3 py-1.5 text-[12px] font-medium outline-none transition focus-visible:ring-2 focus-visible:ring-wave-cyan/60',
            option.value === value
              ? 'bg-white/[0.12] text-white shadow-sm'
              : 'text-wave-muted hover:bg-white/[0.05] hover:text-slate-200',
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

export function SettingsRow({
  title,
  description,
  children,
  className,
}: {
  title: string
  description?: string
  children: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex flex-col gap-3 py-3.5 sm:flex-row sm:items-center sm:justify-between', className)}>
      <div className="min-w-0">
        <p className="text-sm font-medium text-white">{title}</p>
        {description ? <p className="mt-0.5 text-[12px] leading-relaxed text-wave-muted">{description}</p> : null}
      </div>
      <div className="shrink-0">{children}</div>
    </div>
  )
}

export function KeyCap({ children }: { children: ReactNode }) {
  return (
    <kbd className="inline-flex min-w-6 items-center justify-center rounded-md border border-white/15 bg-white/[0.06] px-1.5 py-0.5 font-mono text-[10px] text-slate-300 shadow-[0_1px_0_rgba(255,255,255,.08)_inset]">
      {children}
    </kbd>
  )
}
