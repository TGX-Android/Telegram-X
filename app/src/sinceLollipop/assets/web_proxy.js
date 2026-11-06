/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 21/10/2026
 */
function (origin, pathPrefix, bridgeName, nativeName, useArrayBuffer) {
  'use strict';
  if (window !== window.top || location.origin !== origin) {
    return;
  }
  const native = window[nativeName];
  const maxBytes = 1048584;
  const fail = () => native.postMessage('f');
  const lock = (target, name, value) => {
    Object.defineProperty(target, name, {value, writable: false, configurable: false});
  };
  const allowed = (value, socket) => {
    const url = new URL(value, location.href);
    const scheme = socket ? 'wss:' : 'https:';
    if (url.protocol !== scheme || url.host !== location.host ||
        url.username || url.password || !url.pathname.startsWith(pathPrefix) ||
        /%2e|%2f|%5c/i.test(url.pathname)) {
      throw new TypeError('Relay request blocked');
    }
    return url.href;
  };
  // WebView's request callback does not cover WebSockets or service worker traffic.
  const fetch = window.fetch.bind(window);
  lock(window, 'fetch', (input, init) => {
    const request = new Request(input, init);
    allowed(request.url, false);
    return fetch(new Request(request, {
      redirect: 'error', credentials: 'omit', referrerPolicy: 'no-referrer'
    }));
  });
  const Socket = window.WebSocket;
  function RelaySocket (url, protocols) {
    return new Socket(allowed(url, true), ...(protocols === undefined ? [] : [protocols]));
  }
  RelaySocket.prototype = Socket.prototype;
  lock(Socket.prototype, 'constructor', RelaySocket);
  for (const name of ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED']) {
    lock(RelaySocket, name, Socket[name]);
  }
  lock(window, 'WebSocket', RelaySocket);
  // fetch and WebSocket are the only networking APIs needed by the relay.
  for (const name of ['XMLHttpRequest', 'EventSource', 'Worker', 'SharedWorker', 'WebTransport',
    'RTCPeerConnection', 'webkitRTCPeerConnection', 'RTCDataChannel', 'Notification',
    'localStorage', 'sessionStorage', 'indexedDB', 'caches', 'open']) {
    lock(window, name, undefined);
  }
  for (const name of ['serviceWorker', 'sendBeacon', 'mediaDevices', 'geolocation', 'credentials',
    'storage', 'permissions', 'bluetooth', 'usb', 'serial', 'hid']) {
    lock(navigator, name, undefined);
  }
  const policy = "default-src 'none'; script-src " + origin + pathPrefix + " 'unsafe-inline'; " +
    "style-src " + origin + pathPrefix + " 'unsafe-inline'; img-src " + origin + pathPrefix +
    "; connect-src " + origin + pathPrefix + ' ' + origin.replace('https:', 'wss:') + pathPrefix +
    "; frame-src 'none'; worker-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
  // Chromium ignores a CSP meta outside of <head>, so wait until it exists.
  const applyPolicy = () => {
    if (!document.head) {
      return false;
    }
    const meta = document.createElement('meta');
    meta.httpEquiv = 'Content-Security-Policy';
    meta.content = policy;
    document.head.prepend(meta);
    return true;
  };
  // Same-origin frames keep untouched networking APIs, so scripts must not reach into them.
  for (const frame of [HTMLIFrameElement, HTMLFrameElement, HTMLObjectElement, HTMLEmbedElement]) {
    for (const name of ['contentWindow', 'contentDocument']) {
      Object.defineProperty(frame.prototype, name, {get: () => null, configurable: false});
    }
  }
  for (const frame of [HTMLObjectElement, HTMLEmbedElement]) {
    lock(frame.prototype, 'getSVGDocument', () => null);
  }
  if (!applyPolicy()) {
    const observer = new MutationObserver(() => {
      if (applyPolicy()) {
        observer.disconnect();
      }
    });
    observer.observe(document, {childList: true, subtree: true});
  }
  let receiver = null;
  const bridge = {
    postMessage (value) {
      if (typeof value === 'string') {
        if (value.length > maxBytes) {
          throw new RangeError('Relay message too large');
        }
        native.postMessage('t' + value);
        return;
      }
      let bytes;
      if (value instanceof ArrayBuffer) {
        bytes = new Uint8Array(value);
      } else if (ArrayBuffer.isView(value)) {
        bytes = new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
      } else {
        throw new TypeError('Expected text or binary data');
      }
      if (bytes.length > maxBytes) {
        throw new RangeError('Relay message too large');
      }
      if (useArrayBuffer) {
        const buffer = bytes.buffer;
        native.postMessage(bytes.byteOffset === 0 && bytes.byteLength === buffer.byteLength ?
          buffer : buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
        return;
      }
      const chunks = [];
      for (let i = 0; i < bytes.length; i += 8192) {
        chunks.push(String.fromCharCode.apply(null, bytes.subarray(i, i + 8192)));
      }
      native.postMessage('b' + btoa(chunks.join('')));
    },
    get onmessage () { return receiver; },
    set onmessage (value) {
      receiver = typeof value === 'function' ? value : null;
      if (receiver) {
        native.postMessage('r');
      }
    }
  };
  native.onmessage = event => {
    try {
      const value = event.data;
      if (!receiver) {
        throw new TypeError();
      }
      let data;
      if (useArrayBuffer && value instanceof ArrayBuffer) {
        if (value.byteLength > maxBytes) {
          throw new RangeError();
        }
        data = value;
      } else if (typeof value !== 'string') {
        throw new TypeError();
      } else if (value[0] === 'b') {
        const binary = atob(value.substring(1));
        if (binary.length > maxBytes) {
          throw new RangeError();
        }
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
          bytes[i] = binary.charCodeAt(i);
        }
        data = bytes.buffer;
      } else if (value[0] === 't') {
        data = value.substring(1);
      } else {
        throw new TypeError();
      }
      receiver.call(bridge, {data});
    } catch (error) {
      fail();
    }
  };
  lock(window, bridgeName, Object.freeze(bridge));
}
