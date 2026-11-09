import { useEffect, useRef, useState } from 'react'
import { cn } from '../lib/cn'
import { CHAT_MAX_LENGTH, type ChatMessage, type Participant } from '../lib/protocol'
import { ChatIcon, SendIcon } from './Icons'
import { Avatar, LiveDot, Panel } from './ui'

interface ChatPanelProps {
  messages: ChatMessage[]
  selfId?: string
  connected: boolean
  onSend: (text: string) => boolean
  compact?: boolean
  participants?: Participant[]
  /** Used inside the mobile room tab container so no second card border is drawn. */
  embedded?: boolean
}

const QUICK_REACTIONS = ['👍', '😂', '❤️', '🔥', '🎉', '😮']

/**
 * Room chat for every participant. Rendering is server-driven: the host assigns the
 * sender identity and timestamp, so a viewer can never spoof another name.
 */
export function ChatPanel({
  messages,
  selfId,
  connected,
  onSend,
  compact = false,
  participants = [],
  embedded = false,
}: ChatPanelProps) {
  const [draft, setDraft] = useState('')
  const [sendError, setSendError] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)
  const online = participants.filter((participant) => participant.connected !== false).length

  useEffect(() => {
    const list = listRef.current
    if (!list) return
    list.scrollTop = list.scrollHeight
  }, [messages.length])

  function submit(text: string) {
    const trimmed = text.trim()
    if (!trimmed || !connected) return
    if (onSend(trimmed)) {
      setDraft('')
      setSendError(false)
    } else {
      setSendError(true)
    }
  }

  const body = (
    <>
      <div className={cn('flex items-center justify-between gap-3 border-b border-white/[0.06]', compact ? 'px-3.5 py-2.5' : 'px-4 py-3')}>
        <div className="flex items-center gap-2">
          <ChatIcon size={15} className="text-wave-cyan" />
          <h2 className="text-[11px] font-semibold uppercase tracking-[0.16em] text-wave-muted">Room chat</h2>
        </div>
        <span className="inline-flex items-center gap-1.5 text-[10px] text-wave-muted">
          {connected ? <LiveDot tone="cyan" /> : <span className="size-2 rounded-full bg-wave-amber" />}
          {connected ? `${online > 0 ? `${online} online` : `${messages.length} messages`}` : 'Reconnecting'}
        </span>
      </div>

      <div
        ref={listRef}
        className={cn(
          'min-h-[170px] flex-1 overflow-y-auto',
          compact ? 'max-h-[300px] space-y-2 px-3.5 py-3' : 'max-h-[360px] space-y-3 px-4 py-3',
          embedded && 'max-h-[46vh] min-h-[220px]',
        )}
        aria-live="polite"
      >
        {messages.length === 0 ? (
          <div className="flex h-full min-h-[150px] flex-col items-center justify-center gap-2 text-center">
            <span className="grid size-11 place-items-center rounded-full border border-wave-cyan/20 bg-wave-cyan/[0.08] text-wave-cyan">
              <ChatIcon size={20} />
            </span>
            <p className="text-[13px] font-medium text-white">No messages yet</p>
            <p className="max-w-[220px] text-[11px] leading-relaxed text-wave-muted">
              Say hi, react to a scene, or share a thought. Everyone in the room can chat.
            </p>
          </div>
        ) : (
          messages.map((message) => {
            const mine = message.participantId === selfId
            return (
              <div key={message.messageId} className={cn('flex items-end gap-2', mine && 'flex-row-reverse')}>
                {!mine ? <Avatar name={message.displayName} host={message.isHost} size={compact ? 'xs' : 'sm'} /> : null}
                <div className={cn('min-w-0 max-w-[84%]', mine && 'text-right')}>
                  {!mine ? (
                    <p className="mb-1 flex items-center gap-1.5 text-[10px] font-medium text-wave-muted">
                      <span className={message.isHost ? 'text-wave-amber' : 'text-slate-300'}>{message.displayName}</span>
                      {message.isHost ? <span className="text-[9px] uppercase tracking-wider text-wave-amber">host</span> : null}
                    </p>
                  ) : null}
                  <p
                    className={cn(
                      'inline-block whitespace-pre-wrap break-words rounded-2xl text-left leading-relaxed',
                      compact ? 'px-2.5 py-1.5 text-[12px]' : 'px-3 py-2 text-[13px]',
                      mine
                        ? 'rounded-br-sm bg-wave-cyan/15 text-cyan-50'
                        : 'rounded-bl-sm border border-white/[0.06] bg-white/[0.05] text-slate-200',
                    )}
                  >
                    {message.text}
                  </p>
                  <p className={cn('mt-1 text-[9px] text-wave-muted/70', compact && 'mt-0.5')}>
                    {mine ? 'You · ' : ''}
                    {formatTime(message.sentAt)}
                  </p>
                </div>
              </div>
            )
          })
        )}
      </div>

      <div className={cn('border-t border-white/[0.06]', compact ? 'px-3 pb-3 pt-2' : 'px-3 pb-3 pt-2')}>
        <div className="mb-2 flex items-center gap-0.5 overflow-x-auto no-scrollbar">
          {QUICK_REACTIONS.map((emoji) => (
            <button
              key={emoji}
              type="button"
              disabled={!connected}
              onClick={() => submit(emoji)}
              className="grid size-8 shrink-0 place-items-center rounded-lg text-[15px] transition hover:bg-white/[0.08] disabled:cursor-not-allowed disabled:opacity-40"
              aria-label={`Send ${emoji}`}
              title={`Send ${emoji}`}
            >
              {emoji}
            </button>
          ))}
          <span className="ml-auto pl-2 text-[10px] tabular-nums text-wave-muted/60">
            {draft.length > CHAT_MAX_LENGTH - 100 ? `${draft.length}/${CHAT_MAX_LENGTH}` : ''}
          </span>
        </div>
        <form
          className="flex items-center gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            submit(draft)
          }}
        >
          <input
            value={draft}
            onChange={(event) => {
              setDraft(event.currentTarget.value.slice(0, CHAT_MAX_LENGTH))
              setSendError(false)
            }}
            maxLength={CHAT_MAX_LENGTH}
            disabled={!connected}
            placeholder={connected ? 'Message the room…' : 'Reconnecting to chat…'}
            aria-label="Chat message"
            className="h-10 min-w-0 flex-1 rounded-xl border border-white/10 bg-ink-900/80 px-3 text-[13px] text-white placeholder:text-wave-muted/45 outline-none transition focus:border-wave-cyan/50 focus:ring-4 focus:ring-wave-cyan/10 disabled:cursor-not-allowed disabled:opacity-50"
          />
          <button
            type="submit"
            disabled={!connected || draft.trim().length === 0}
            className="grid size-10 shrink-0 place-items-center rounded-xl bg-wave-cyan text-ink-950 outline-none transition hover:brightness-110 focus-visible:ring-2 focus-visible:ring-wave-cyan/70 disabled:cursor-not-allowed disabled:opacity-40"
            aria-label="Send message"
            title="Send message"
          >
            <SendIcon size={17} />
          </button>
        </form>
        {sendError ? (
          <p className="mt-2 text-[11px] text-wave-amber">Chat is reconnecting. Try again in a moment.</p>
        ) : null}
      </div>
    </>
  )

  if (embedded) return <div className="flex flex-col overflow-hidden rounded-xl">{body}</div>
  return <Panel className="flex min-h-[360px] flex-col overflow-hidden">{body}</Panel>
}

function formatTime(value: number): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}
