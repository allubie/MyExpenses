module.exports = {
  content: [
        '../webui/src/main/assets/form.html'
      ],
  safelist: [
    'italic',
    'underline',
    'font-semibold',
      {
            pattern: /grid-cols-(1|2|3|4|5|6|7|8|9|10|11|12)/,
      }
    ],
  theme: {
    extend: {},
  },
  variants: {
    extend: {
      opacity: ['disabled'],
    }
  },
  plugins: [],
}
