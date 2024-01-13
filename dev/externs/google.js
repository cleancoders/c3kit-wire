// externs for Google oauth coming from https://developers.google.com/identity/gsi/web/reference/js-reference

function google() {}

google.account = function () {};
google.prototype.account = google.account;

google.account.id = function () {};
google.prototype.account.id = google.account.id;

google.account.id.initialize = function (config) {};
google.prototype.account.id.initialize = google.account.id.initialize;

google.account.id.renderButton = function (node, options) {};
google.prototype.account.id.renderButton = google.account.id.renderButton;

google.account.id.prompt = function (handler) {};
google.prototype.account.id.prompt = google.account.id.prompt;

google.account.id.cancel = function () {};
google.prototype.account.id.cancel = google.account.id.cancel;

google.account.id.disableAutoSelect = function () {};
google.prototype.account.id.disableAutoSelect = google.account.id.disableAutoSelect;

google.account.id.storeCredential = function (credential, callback) {};
google.prototype.account.id.storeCredential = google.account.id.storeCredential;

google.account.id.revoke = function (hint, callback) {};
google.prototype.account.id.revoke = google.account.id.revoke;
