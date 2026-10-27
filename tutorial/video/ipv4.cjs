// WSL mirrored networking quirks break Remotion's local server:
// - a server bound to "::" does not receive IPv4 loopback connections, and
// - after probing a port on 127.0.0.1, binding 0.0.0.0 on it silently gets a random port.
// Hide external IPv6 interfaces (so Remotion binds IPv4) and bind loopback instead of 0.0.0.0.
const os = require('os');
const net = require('net');
const interfaces = os.networkInterfaces;
os.networkInterfaces = () => Object.fromEntries(Object.entries(interfaces()).map(([name, list]) =>
  [name, list.filter((c) => c.family !== 'IPv6' || c.internal)]));
const listen = net.Server.prototype.listen;
net.Server.prototype.listen = function (options, ...rest) {
  if (options && typeof options === 'object' && options.host === '0.0.0.0') options = { ...options, host: '127.0.0.1' };
  return listen.call(this, options, ...rest);
};
