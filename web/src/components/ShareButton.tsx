import { useEffect, useRef, useState } from 'react'
import { copyText } from '../lib/clipboard'
import { CheckIcon, CopyIcon, ShareIcon } from './Icons'
import { Button, type ButtonProps } from './ui'

interface ShareButtonProps extends Omit<ButtonProps, 'children' | 'onClick'> {
  url: string
  label?: string
  compact?: boolean
}

export function ShareButton({ url, label = 'Copy invite', compact = false, ...props }: ShareButtonProps) {
  const [status, setStatus] = useState<'idle' | 'copied' | 'failed'>('idle')
  const timer = useRef<number | null>(null)

  useEffect(
    () => () => {
      if (timer.current !== null) window.clearTimeout(timer.current)
    },
    [],
  )

  return (
    <Button
      variant="secondary"
      size={compact ? 'sm' : 'md'}
      {...props}
      onClick={async () => {
        const ok = await copyText(url)
        setStatus(ok ? 'copied' : 'failed')
        if (timer.current !== null) window.clearTimeout(timer.current)
        timer.current = window.setTimeout(() => setStatus('idle'), 2_500)
      }}
      aria-live="polite"
    >
      {status === 'copied' ? (
        <CheckIcon size={16} className="text-wave-green" />
      ) : status === 'failed' ? (
        <ShareIcon size={16} className="text-wave-amber" />
      ) : (
        <CopyIcon size={16} />
      )}
      {status === 'copied' ? 'Copied' : status === 'failed' ? 'Copy failed' : label}
    </Button>
  )
}
