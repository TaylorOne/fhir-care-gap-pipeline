// Full Karma config (a custom karmaConfig replaces the builder's defaults, so
// the frameworks/plugins must be declared here too). Adds a no-sandbox
// Chromium launcher for containerized environments where Chrome cannot use
// its sandbox (root user / restricted kernel).
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
    ],
    client: {
      jasmine: {},
      clearContext: false,
    },
    reporters: ['progress', 'kjhtml'],
    customLaunchers: {
      ChromeHeadlessNoSandbox: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
      },
    },
    browsers: ['Chrome'],
    restartOnFileChange: true,
  });
};
