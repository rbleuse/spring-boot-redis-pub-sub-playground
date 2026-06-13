export const environment = {
  production: true,
  apiBaseUrl: '',                       // same-origin behind nginx
  wsUrl: `${location.origin.replace(/^http/, 'ws')}/ws`,
};
