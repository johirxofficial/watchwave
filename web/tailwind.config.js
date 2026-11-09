/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          950: 'rgb(var(--wv-ink-950) / <alpha-value>)',
          900: 'rgb(var(--wv-ink-900) / <alpha-value>)',
          850: 'rgb(var(--wv-ink-850) / <alpha-value>)',
          800: 'rgb(var(--wv-ink-800) / <alpha-value>)',
          700: 'rgb(var(--wv-ink-700) / <alpha-value>)',
          600: 'rgb(var(--wv-ink-600) / <alpha-value>)',
        },
        wave: {
          cyan: 'rgb(var(--wv-accent) / <alpha-value>)',
          teal: 'rgb(var(--wv-accent-2) / <alpha-value>)',
          amber: 'rgb(255 183 77 / <alpha-value>)',
          green: 'rgb(74 222 128 / <alpha-value>)',
          red: 'rgb(255 107 107 / <alpha-value>)',
          muted: 'rgb(var(--wv-muted) / <alpha-value>)',
          soft: 'rgb(var(--wv-soft) / <alpha-value>)',
        },
      },
      fontFamily: {
        sans: [
          'Inter',
          'SF Pro Text',
          'Segoe UI Variable Text',
          'Segoe UI',
          'system-ui',
          '-apple-system',
          'sans-serif',
        ],
        display: [
          'Sora',
          'SF Pro Display',
          'Segoe UI Variable Display',
          'Segoe UI',
          'system-ui',
          'sans-serif',
        ],
        mono: ['JetBrains Mono', 'Cascadia Code', 'Consolas', 'ui-monospace', 'monospace'],
      },
      boxShadow: {
        glow: '0 0 0 1px rgb(var(--wv-accent) / .18), 0 24px 80px -36px rgb(var(--wv-accent) / .45)',
        panel: '0 30px 90px -60px rgba(0,0,0,.95)',
        float: '0 18px 50px -28px rgba(0,0,0,.9)',
      },
      keyframes: {
        'fade-up': {
          from: { opacity: '0', transform: 'translateY(12px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        pulseSoft: {
          '0%,100%': { opacity: '0.55' },
          '50%': { opacity: '1' },
        },
        floaty: {
          '0%,100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-8px)' },
        },
      },
      animation: {
        'fade-up': 'fade-up .5s cubic-bezier(.2,.8,.2,1) both',
        'fade-in': 'fade-in .35s ease-out both',
        shimmer: 'shimmer 2.4s linear infinite',
        'pulse-soft': 'pulseSoft 2s ease-in-out infinite',
        floaty: 'floaty 7s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}
