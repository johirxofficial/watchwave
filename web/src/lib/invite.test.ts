import { describe, expect, it } from 'vitest'
import { buildRoomPath, parseInvite, readToken } from './invite'

describe('invite parsing', () => {
  it('parses a public HTTPS invite', () => {
    expect(parseInvite('https://wave.example.com/room/abc123xyz?t=secret-token')).toEqual({
      roomId: 'abc123xyz',
      inviteToken: 'secret-token',
    })
  })

  it('parses a LAN invite with a port', () => {
    expect(parseInvite('http://192.168.1.10:8080/room/k3f9xz2m7q1a?t=tok%2Bvalue')).toEqual({
      roomId: 'k3f9xz2m7q1a',
      inviteToken: 'tok+value',
    })
  })

  it('parses the custom deep link scheme', () => {
    expect(parseInvite('watchwave://room/abc123xyz?t=deep')).toEqual({
      roomId: 'abc123xyz',
      inviteToken: 'deep',
    })
  })

  it('parses a bare room code without a token', () => {
    expect(parseInvite('  abc123xyz ')).toEqual({ roomId: 'abc123xyz', inviteToken: null })
  })

  it('rejects malformed rooms and arbitrary URLs', () => {
    expect(parseInvite('')).toBeNull()
    expect(parseInvite('https://wave.example.com/watch/abc123')).toBeNull()
    expect(parseInvite('https://wave.example.com/room/ABC!')).toBeNull()
    expect(parseInvite('not a link')).toBeNull()
  })

  it('ignores fragments when reading the token', () => {
    expect(readToken('/room/abc123?t=hello#fragment')).toBe('hello')
    expect(readToken('/room/abc123#t=no')).toBeNull()
  })

  it('builds an encoded room path', () => {
    expect(buildRoomPath('abc123', 'a b+c')).toBe('/room/abc123?t=a%20b%2Bc')
    expect(buildRoomPath('abc123')).toBe('/room/abc123')
  })
})
