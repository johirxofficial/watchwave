/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          950: '#06070b',
          900: '#090b11',
          850: '#0d1018',
          800: '#11151f',
          700: '#171c29',
          600: '#222939',
        },
        wave: {
          cyan: '#22d3ee',
          teal: '#2dd4bf',
          amber: '#fbbf24',
          green: '#34d399',
          red: '#fb7185',
          muted: '#8a93a6',
        },
      },
      fontFamily: {
        sans: [
          'Inter',
          'Segoe UI Variable Text',
          'Segoe UI',
          'system-ui',
          '-apple-system',
          'sans-serif',
        ],
        display: [
          'Bahnschrift',
          'Segoe UI Variable Display',
          'Segoe UI',
          'system-ui',
          'sans-serif',
        ],
        mono: ['Cascadia Code', 'Consolas', 'ui-monospace', 'monospace'],
      },
      boxShadow: {
        glow: '0 0 0 1px rgba(34,211,238,.16), 0 24px 70px -32px rgba(34,211,238,.35)',
        panel: '0 24px 80px -48px rgba(0,0,0,.9)',
      },
      keyframes: {
        'fade-up': {
          from: { opacity: '0', transform: 'translateY(10px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        pulseSoft: {
          '0%,100%': { opacity: '0.55' },
          '50%': { opacity: '1' },
        },
      },
      animation: {
        'fade-up': 'fade-up .45s ease-out both',
        shimmer: 'shimmer 2.4s linear infinite',
        'pulse-soft': 'pulseSoft 2s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}
