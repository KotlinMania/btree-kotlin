const client = config.client || {};

config.set({
  browserNoActivityTimeout: 120000,
  browserDisconnectTimeout: 120000,
  browserDisconnectTolerance: 1,
  client: {
    ...client,
    mocha: {
      ...(client.mocha || {}),
      timeout: 120000
    }
  }
});
