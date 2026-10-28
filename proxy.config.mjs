const backendHost = 'discographyx-backend';
const backendPort = 8080;

/**
 * @type {import('vite').CommonServerOptions['proxy']}
 */
export default {
  '^/(api|management|v3/api-docs)': {
    target: `http://${backendHost}:${backendPort}`,
    xfwd: true,
  },
};
