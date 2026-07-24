'use strict';

if (typeof __nativeLog !== 'function') {
    globalThis.__nativeLog = function() {};
}

globalThis.exports = {};
globalThis.module = { exports: globalThis.exports };
globalThis.__lxModule = { exports: {} };
globalThis.__pluginInstance = null;
globalThis.plugin = {};
globalThis.Plugin = function() {};
globalThis.handler = {};

globalThis.console = {
    log: function() { __nativeLog('[LOG] ' + Array.prototype.slice.call(arguments).join(' ')); },
    warn: function() { __nativeLog('[WARN] ' + Array.prototype.slice.call(arguments).join(' ')); },
    error: function() { __nativeLog('[ERR] ' + Array.prototype.slice.call(arguments).join(' ')); },
    info: function() { __nativeLog('[INFO] ' + Array.prototype.slice.call(arguments).join(' ')); }
};

if (typeof globalThis.highlight !== 'function') {
    globalThis.highlight = function(text) { return text || ''; };
}
if (typeof globalThis.unescapeHTML !== 'function') {
    globalThis.unescapeHTML = function(html) {
        if (!html || typeof html !== 'string') return html;
        return html.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&#x27;/g, "'");
    };
}
if (typeof globalThis.decodeName !== 'function') {
    globalThis.decodeName = function(name) {
        if (!name || typeof name !== 'string') return name || '';
        return name.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&#x27;/g, "'").replace(/<[^>]*>/g, '');
    };
}
if (typeof globalThis.formatDuration !== 'function') {
    globalThis.formatDuration = function(sec) {
        if (typeof sec === 'string') { var p = sec.split(':'); if (p.length >= 2) return parseInt(p[0]) * 60 + parseInt(p[1]); return parseInt(sec) || 0; }
        return parseInt(sec) || 0;
    };
}

globalThis.env = {
    getUserVariables: function() { return {}; },
    userVariables: {},
    appVersion: '1.0',
    os: 'android',
    lang: 'zh-CN'
};

globalThis.process = {
    platform: 'android',
    version: '1.0',
    env: globalThis.env
};

globalThis.URL = function(url) {
    if (!(this instanceof globalThis.URL)) {
        return new globalThis.URL(url);
    }
    this.href = url;
    this.toString = function() { return url; };
    this.protocol = '';
    this.hostname = '';
    this.pathname = '';
    this.origin = '';
    this.host = '';
    this.search = '';
    this.hash = '';
    try {
        var parts = url.split('://');
        if (parts.length > 1) {
            this.protocol = parts[0] + ':';
            var rest = parts[1];
            var pathIdx = rest.indexOf('/');
            if (pathIdx !== -1) {
                this.hostname = rest.substring(0, pathIdx);
                this.pathname = rest.substring(pathIdx);
                this.host = this.hostname;
            } else {
                this.hostname = rest;
                this.host = rest;
            }
            var hashIdx = this.pathname.indexOf('#');
            if (hashIdx !== -1) {
                this.hash = this.pathname.substring(hashIdx);
                this.pathname = this.pathname.substring(0, hashIdx);
            }
            var searchIdx = this.pathname.indexOf('?');
            if (searchIdx !== -1) {
                this.search = this.pathname.substring(searchIdx);
                this.pathname = this.pathname.substring(0, searchIdx);
            }
        }
        this.origin = this.protocol + '//' + this.host;
    } catch(e) {}
};

globalThis.URLSearchParams = function(init) {
    this.params = {};
    if (typeof init === 'string') {
        var str = init.charAt(0) === '?' ? init.substring(1) : init;
        str.split('&').forEach(function(pair) {
            var idx = pair.indexOf('=');
            if (idx > 0) {
                this.params[decodeURIComponent(pair.substring(0, idx))] = decodeURIComponent(pair.substring(idx + 1));
            } else if (pair.length > 0) {
                this.params[decodeURIComponent(pair)] = '';
            }
        }.bind(this));
    }
    this.get = function(name) { return this.params[name] || null; };
    this.set = function(name, val) { this.params[name] = String(val); };
    this.append = function(name, val) {
        var existing = this.params[name];
        this.params[name] = existing ? existing + ',' + val : String(val);
    };
    this.delete = function(name) { delete this.params[name]; };
    this.has = function(name) { return name in this.params; };
    this.keys = function() { return Object.keys(this.params); };
    this.values = function() {
        var vals = [];
        Object.keys(this.params).forEach(function(k) { vals.push(this.params[k]); }.bind(this));
        return vals;
    };
    this.forEach = function(cb) {
        Object.keys(this.params).forEach(function(k) { cb(this.params[k], k, this); }.bind(this));
    };
    this.toString = function() {
        return Object.keys(this.params).map(function(k) {
            return encodeURIComponent(k) + '=' + encodeURIComponent(this.params[k]);
        }.bind(this)).join('&');
    };
};

globalThis.TextEncoder = function() {
    this.encode = function(str) {
        var arr = [];
        for (var i = 0; i < str.length; i++) {
            var code = str.charCodeAt(i);
            if (code < 128) arr.push(code);
            else if (code < 2048) { arr.push(192 | (code >> 6)); arr.push(128 | (code & 63)); }
            else { arr.push(224 | (code >> 12)); arr.push(128 | ((code >> 6) & 63)); arr.push(128 | (code & 63)); }
        }
        return new Uint8Array(arr);
    };
};

globalThis.TextDecoder = function() {
    this.decode = function(bytes) {
        var str = '';
        var i = 0;
        while (i < bytes.length) {
            var byte1 = bytes[i++];
            if (byte1 < 128) { str += String.fromCharCode(byte1); }
            else if (byte1 >= 192 && byte1 < 224) {
                var byte2 = bytes[i++];
                str += String.fromCharCode(((byte1 & 31) << 6) | (byte2 & 63));
            } else {
                var byte2 = bytes[i++];
                var byte3 = bytes[i++];
                str += String.fromCharCode(((byte1 & 15) << 12) | ((byte2 & 63) << 6) | (byte3 & 63));
            }
        }
        return str;
    };
};

globalThis.__pendingNativeRequests = {};

globalThis.__toAxiosResponse = function(fetchResponse, url) {
    var bodyStr = '';
    if (typeof fetchResponse.body === 'string') {
        bodyStr = fetchResponse.body;
    } else if (typeof fetchResponse._bodyText === 'string') {
        bodyStr = fetchResponse._bodyText;
    } else if (fetchResponse.body && typeof fetchResponse.body === 'object') {
        var data = fetchResponse.body;
        if (data === undefined || data === null) data = {};
        __nativeLog('[TO_AXIOS] body is already object, keys=' + (typeof data === 'object' ? Object.keys(data).join(',').substring(0,80) : 'not-obj'));
        var axiosResp = {
            data: data,
            status: fetchResponse.status || 200,
            statusText: fetchResponse.statusText || 'OK',
            headers: fetchResponse.headers || {},
            config: {},
            request: { responseURL: url || '' },
            ok: fetchResponse.ok !== false
        };
        return axiosResp;
    }
    var data = bodyStr;
    if (typeof bodyStr === 'string' && bodyStr.length > 0) {
        try {
            data = JSON.parse(bodyStr);
        } catch(e1) {
            try {
                var trimmed = bodyStr.replace(/^\s+/, '');
                var jsonStart = trimmed.indexOf('{');
                if (jsonStart < 0) jsonStart = trimmed.indexOf('[');
                if (jsonStart > 0) {
                    data = JSON.parse(trimmed.substring(jsonStart));
                } else if (jsonStart === 0) {
                    data = JSON.parse(trimmed);
                } else {
                    __nativeLog('[TO_AXIOS] JSON parse failed and no JSON found in body: ' + e1.message);
                }
            } catch(e2) {
                __nativeLog('[TO_AXIOS] JSON parse failed even after trim: ' + e2.message + ' bodyStart=' + bodyStr.substring(0, 50).replace(/\n/g, '\\n').replace(/\r/g, '\\r'));
            }
        }
        if (typeof data === 'string' && data.length > 0) {
            try { data = JSON.parse(data); } catch(e3) {}
        }
    }
    if (data === undefined || data === null) data = bodyStr || '';
    __nativeLog('[TO_AXIOS] bodyLen=' + bodyStr.length + ' dataType=' + typeof data + ' isObj=' + (data !== null && typeof data === 'object'));
    var axiosResp = {
        data: data,
        status: fetchResponse.status || 200,
        statusText: fetchResponse.statusText || 'OK',
        headers: fetchResponse.headers || {},
        config: {},
        request: { responseURL: url || '' },
        ok: fetchResponse.ok !== false
    };
    __nativeLog('[TO_AXIOS] result: data=' + (typeof axiosResp.data === 'object' ? 'obj(keys=' + Object.keys(axiosResp.data).join(',').substring(0,80) + ')' : typeof axiosResp.data));
    return axiosResp;
};

globalThis.fetch = function(input, init) {
    init = init || {};
    var url = typeof input === 'string' ? input : (input.url || '');
    var method = (init.method || 'GET').toUpperCase();
    __nativeLog('[FETCH] called: ' + method + ' ' + (url ? url.substring(0, 120) : 'EMPTY_URL'));
    var headers = {};
    if (init.headers) {
        if (typeof init.headers.forEach === 'function') {
            init.headers.forEach(function(v, k) { headers[k] = v; });
        } else if (typeof init.headers.entries === 'function') {
            init.headers.entries().forEach(function(kv) { headers[kv[0]] = kv[1]; });
        } else if (typeof init.headers === 'object') {
            Object.keys(init.headers).forEach(function(k) {
                var v = init.headers[k];
                if (k === 'common' && typeof v === 'object') {
                    Object.keys(v).forEach(function(ck) { if (typeof v[ck] === 'string') headers[ck] = v[ck]; });
                } else if (typeof v === 'string') {
                    headers[k] = v;
                }
            });
        }
    }
    var body = init.body || '';
    try {
        __nativeLog('[FETCH] calling __nativeRequest: ' + method + ' ' + (url ? url.substring(0, 80) : ''));
        var resultJson = globalThis.__nativeRequest(method, url, JSON.stringify(headers), typeof body === 'object' ? JSON.stringify(body) : String(body));
        __nativeLog('[FETCH] __nativeRequest returned: ' + (resultJson ? resultJson.length : 'null') + ' chars');
        if (!resultJson || resultJson === '') {
            __nativeLog('[FETCH] ERROR: empty response');
            return Promise.reject(new Error('fetch: empty response from native'));
        }
        var result = JSON.parse(resultJson);
        var response = {
            ok: result.statusCode >= 200 && result.statusCode < 300,
            status: result.statusCode,
            statusText: result.statusCode === 200 ? 'OK' : 'Error',
            headers: result.headers || {},
            json: function() { return Promise.resolve(JSON.parse(result.body || '{}')); },
            text: function() { return Promise.resolve(result.body || ''); },
            body: result.body
        };
        __nativeLog('[FETCH] success, status=' + result.statusCode + ' bodyLen=' + (result.body ? result.body.length : 0) + ' preview=' + (result.body ? result.body.substring(0, 200) : ''));
        return Promise.resolve(response);
    } catch(e) {
        __nativeLog('[FETCH] ERROR: ' + e.message);
        return Promise.reject(new Error('fetch error: ' + e.message));
    }
};

globalThis.lx = (function() {
    var _EVENT_NAMES = { request: 'request', inited: 'inited', updateAlert: 'updateAlert' };
    var _instance = {
        EVENT_NAMES: _EVENT_NAMES,
        on: function(eventName, handler) {
            if (!Object.values(_EVENT_NAMES).includes(eventName)) { __nativeLog('[LX] warning: unsupported event for lx.on: ' + eventName); return Promise.resolve(); }
            switch (eventName) {
                case _EVENT_NAMES.request:
                    globalThis.__lxRequestHandler = handler;
                    __nativeLxOn('request');
                    __nativeLog('[LX] request handler registered: ' + (typeof handler));
                    break;
                default: { __nativeLog('[LX] warning: unsupported event for lx.on: ' + eventName); return Promise.resolve(); }
            }
            return Promise.resolve();
        },
        send: function(eventName, data) {
            if (!Object.values(_EVENT_NAMES).includes(eventName)) { __nativeLog('[LX] warning: unsupported event for lx.send: ' + eventName); return Promise.resolve(); }
            switch (eventName) {
                case _EVENT_NAMES.inited:
                    __nativeLxSend('inited', typeof data === 'string' ? data : JSON.stringify(data || {}));
                    __nativeLog('[LX] inited sent: ' + JSON.stringify(data).substring(0, 200));
                    break;
                case _EVENT_NAMES.updateAlert:
                    __nativeLog('[LX] updateAlert sent');
                    break;
                default:
                    __nativeLog('[LX] warning: unknown event for lx.send: ' + eventName);
                    break;
            }
            return Promise.resolve();
        },
    request: function(url, options, callback) {
        options = options || {};
        var method = (options.method || 'GET').toUpperCase();
        var headers = options.headers || {};
        var body = options.body || '';
        if (options.form) {
            var formBody = [];
            for (var k in options.form) { formBody.push(encodeURIComponent(k) + '=' + encodeURIComponent(options.form[k])); }
            body = formBody.join('&');
            if (!headers['Content-Type']) headers['Content-Type'] = 'application/x-www-form-urlencoded';
        }
        if (method === 'POST' && !headers['Content-Type']) {
            if (options.formData) {
                headers['Content-Type'] = 'multipart/form-data';
                body = options.formData;
                delete options.formData;
            } else {
                headers['Content-Type'] = 'application/json';
            }
        }
        if (headers['Content-Type'] === 'application/json' && typeof body === 'object') {
            body = JSON.stringify(body);
        }
        var defaultHeaders = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36',
            'Accept': 'application/json'
        };
        var mergedHeaders = {};
        for (var k in defaultHeaders) { mergedHeaders[k] = defaultHeaders[k]; }
        for (var k in headers) { mergedHeaders[k] = headers[k]; }
        var timeout = (options.timeout && typeof options.timeout === 'number' && options.timeout > 0) ? Math.min(options.timeout, 60000) : 15000;
        __nativeLog('[LX_REQUEST] ' + method + ' ' + (url || '').substring(0, 120));
        var requestInfo = { aborted: false, abort: function() { this.aborted = true; } };
        try {
            var resultJson = globalThis.__nativeRequest(method, url, JSON.stringify(mergedHeaders), typeof body === 'object' ? JSON.stringify(body) : String(body));
            var result = JSON.parse(resultJson);
            if (typeof result.body === 'string') {
                try { result.body = JSON.parse(result.body); } catch(e) {}
            }
            __nativeLog('[LX_REQUEST] done status=' + result.statusCode + ' bodyType=' + typeof result.body);
            if (callback) callback(null, {
                statusCode: result.statusCode,
                statusMessage: result.statusMessage || '',
                headers: result.headers || {},
                body: result.body,
            }, result.body);
        } catch(e) {
            __nativeLog('[LX_REQUEST] error: ' + e.message);
            if (callback) callback(new Error(e.message || 'request failed'), null, null);
        }
        return function() { requestInfo.aborted = true; };
    },
    utils: {
        crypto: {
            aesEncrypt: function(buffer, mode, key, iv) { throw new Error('lx.utils.crypto.aesEncrypt not implemented'); },
            rsaEncrypt: function(buffer, key) { throw new Error('lx.utils.crypto.rsaEncrypt not implemented'); },
            randomBytes: function(size) {
                var byteArray = new Uint8Array(size);
                for (var i = 0; i < size; i++) { byteArray[i] = Math.floor(Math.random() * 256); }
                return byteArray;
            },
            md5: function(str) {
                if (typeof str !== 'string') throw new Error('param required a string');
                str = String(str);
                var md5Chars = '0123456789abcdef';
                function leftRotate(n, s) { return (n << s) | (n >>> (32 - s)); }
                function addUnsigned(x, y) { var x8 = x & 0x80000000, y8 = y & 0x80000000; return (x & 0x3fffffff) + (y & 0x3fffffff) ^ (x8 ^ y8); }
                function F(x,y,z){return(x&y)|((~x)&z);}function G(x,y,z){return(x&z)|(y&(~z));}function H(x,y,z){return x^y^z;}function I(x,y,z){return y^(x|(~z));}
                var T = [0xd76aa478,0xe8c7b756,0x242070db,0xc1bdceee,0xf57c0faf,0x4787c62a,0xa8304613,0xfd469501,0x698098d8,0x8b44f7af,0xffff5bb1,0x895cd7be,0x6b901122,0xfd987193,0xa679438e,0x49b40821,
                    0xf61e2562,0xc040b340,0x265e5a51,0xe9b6c7aa,0xd62f105d,0x02441453,0xd8a1e681,0xe7d3fbc8,0x21e1cde6,0xc33707d6,0xf4d50d87,0x455a14ed,0xa9e3e905,0xfcefa3f8,0x676f02d9,0x8d2a4c8a,
                    0xfffa3942,0x8771f681,0x6d9d6122,0xfde5380c,0xa4beea44,0x4bdecfa9,0xf6bb4b60,0xbebfbc70,0x289b7ec6,0xeaa127fa,0xd4ef3085,0x04881d05,0xd9d4d039,0xe6db99e9,0x1fa27cf8,
                    0xc4ac5665,0xf4292244,0x432aff97,0xab9423a7,0xfc93a039,0x655b59c3,0x8f0ccc92,0xffeff47d,0x85845dd1,0x6fa87e4f,0xfe2ce6e0,0xa3014314,0x4e0811a1,0xf7537e82,0xbd3af235,
                    0x2ad7d2bb,0xeb86d391];
                var S11=7,S12=12,S13=17,S14=22,S21=5,S22=9,S23=14,S24=20,S31=4,S32=11,S33=16,S34=23,S41=6,S42=10,S43=15,S44=21;
                function FF(a,b,c,d,x,s,ac){a=addUnsigned(a,addUnsigned(addUnsigned(F(b,c,d),x),ac));return addUnsigned(leftRotate(a,s),b);}
                function GG(a,b,c,d,x,s,ac){a=addUnsigned(a,addUnsigned(addUnsigned(G(b,c,d),x),ac));return addUnsigned(leftRotate(a,s),b);}
                function HH(a,b,c,d,x,s,ac){a=addUnsigned(a,addUnsigned(addUnsigned(H(b,c,d),x),ac));return addUnsigned(leftRotate(a,s),b);}
                function II(a,b,c,d,x,s,ac){a=addUnsigned(a,addUnsigned(addUnsigned(I(b,c,d),x),ac));return addUnsigned(leftRotate(a,s),b);}
                function cvtHex(n){var h='';for(var j=0;j<=3;j++){var v=(n>>(j*8))&255;h+=md5Chars.charAt((v>>>4)&0xf)+md5Chars.charAt(v&0xf);}return h;}
                var len=str.length*8;str+=String.fromCharCode(128);while(str.length%64!==56){str+=String.fromCharCode(0);}
                str+=String.fromCharCode(len&0xff,(len>>8)&0xff,(len>>16)&0xff,(len>>24)&0xff,0,0,0,0);
                var words=[];for(var i=0;i<str.length;i+=4){words.push(str.charCodeAt(i)|(str.charCodeAt(i+1)<<8)|(str.charCodeAt(i+2)<<16)|(str.charCodeAt(i+3)<<24));}
                var a=0x67452301,b=0xefcdab89,c=0x98badcfe,d=0x10325476;for(var off=0;off<words.length;off+=16){
                    var aa=a,bb=b,cc=c,dd=d;
                    a=FF(a,b,c,d,words[off],S11,T[0]);d=FF(d,a,b,c,words[off+1],S12,T[1]);c=FF(c,d,a,b,words[off+2],S13,T[2]);b=FF(b,c,d,a,words[off+3],S14,T[3]);
                    a=FF(a,b,c,d,words[off+4],S11,T[4]);d=FF(d,a,b,c,words[off+5],S12,T[5]);c=FF(c,d,a,b,words[off+6],S13,T[6]);b=FF(b,c,d,a,words[off+7],S14,T[7]);
                    a=FF(a,b,c,d,words[off+8],S11,T[8]);d=FF(d,a,b,c,words[off+9],S12,T[9]);c=FF(c,d,a,b,words[off+10],S13,T[10]);b=FF(b,c,d,a,words[off+11],S14,T[11]);
                    a=FF(a,b,c,d,words[off+12],S11,T[12]);d=FF(d,a,b,c,words[off+13],S12,T[13]);c=FF(c,d,a,b,words[off+14],S13,T[14]);b=FF(b,c,d,a,words[off+15],S14,T[15]);
                    a=GG(a,b,c,d,words[off+1],S21,T[16]);d=GG(d,a,b,c,words[off+6],S22,T[17]);c=GG(c,d,a,b,words[off+11],S23,T[18]);b=GG(b,c,d,a,words[off],S24,T[19]);
                    a=GG(a,b,c,d,words[off+5],S21,T[20]);d=GG(d,a,b,c,words[off+10],S22,T[21]);c=GG(c,d,a,b,words[off+15],S23,T[22]);b=GG(b,c,d,a,words[off+4],S24,T[23]);
                    a=GG(a,b,c,d,words[off+9],S21,T[24]);d=GG(d,a,b,c,words[off+14],S22,T[25]);c=GG(c,d,a,b,words[off+3],S23,T[26]);b=GG(b,c,d,a,words[off+8],S24,T[27]);
                    a=GG(a,b,c,d,words[off+13],S21,T[28]);d=GG(d,a,b,c,words[off+2],S22,T[29]);c=GG(c,d,a,b,words[off+7],S23,T[30]);b=GG(b,c,d,a,words[off+12],S24,T[31]);
                    a=HH(a,b,c,d,words[off+5],S31,T[32]);d=HH(d,a,b,c,words[off+8],S32,T[33]);c=HH(c,d,a,b,words[off+11],S33,T[34]);b=HH(b,c,d,a,words[off+14],S34,T[35]);
                    a=HH(a,b,c,d,words[off+1],S31,T[36]);d=HH(d,a,b,c,words[off+4],S32,T[37]);c=HH(c,d,a,b,words[off+7],S33,T[38]);b=HH(b,c,d,a,words[off+10],S34,T[39]);
                    a=HH(a,b,c,d,words[off+13],S31,T[40]);d=HH(d,a,b,c,words[off],S32,T[41]);c=HH(c,d,a,b,words[off+3],S33,T[42]);b=HH(b,c,d,a,words[off+6],S34,T[43]);
                    a=HH(a,b,c,d,words[off+9],S31,T[44]);d=HH(d,a,b,c,words[off+12],S32,T[45]);c=HH(c,d,a,b,words[off+15],S33,T[46]);b=HH(b,c,d,a,words[off+2],S34,T[47]);
                    a=II(a,b,c,d,words[off],S41,T[48]);d=II(d,a,b,c,words[off+7],S42,T[49]);c=II(c,d,a,b,words[off+14],S43,T[50]);b=II(b,c,d,a,words[off+5],S44,T[51]);
                    a=II(a,b,c,d,words[off+12],S41,T[52]);d=II(d,a,b,c,words[off+3],S42,T[53]);c=II(c,d,a,b,words[off+10],S43,T[54]);b=II(b,c,d,a,words[off+1],S44,T[55]);
                    a=II(a,b,c,d,words[off+8],S41,T[56]);d=II(d,a,b,c,words[off+15],S42,T[57]);c=II(c,d,a,b,words[off+6],S43,T[58]);b=II(b,c,d,a,words[off+13],S44,T[59]);
                    a=II(a,b,c,d,words[off+4],S41,T[60]);d=II(d,a,b,c,words[off+11],S42,T[61]);c=II(c,d,a,b,words[off+2],S43,T[62]);b=II(b,c,d,a,words[off+9],S44,T[63]);
                    a=addUnsigned(a,aa);b=addUnsigned(b,bb);c=addUnsigned(c,cc);d=addUnsigned(d,dd);
                }return cvtHex(a)+cvtHex(b)+cvtHex(c)+cvtHex(d);
            },
        },
        buffer: {
            from: function(input, encoding) {
                if (typeof input === 'string') {
                    switch (encoding) {
                        case 'base64':
                            var binaryStr = atob(input);
                            var bytes = new Uint8Array(binaryStr.length);
                            for (var i = 0; i < binaryStr.length; i++) bytes[i] = binaryStr.charCodeAt(i);
                            return bytes;
                        case 'hex':
                            var hexStr = input.match(/.{1,2}/g);
                            return new Uint8Array(hexStr.map(function(byte) { return parseInt(byte, 16); }));
                        default:
                            var bytes2 = [];
                            for (var j = 0; j < input.length; j++) {
                                var charCode = input.charCodeAt(j);
                                if (charCode < 128) bytes2.push(charCode);
                                else if (charCode < 2048) { bytes2.push((charCode >> 6) | 192); bytes2.push((charCode & 63) | 128); }
                                else { bytes2.push((charCode >> 12) | 224); bytes2.push(((charCode >> 6) & 63) | 128); bytes2.push((charCode & 63) | 128); }
                            }
                            return new Uint8Array(bytes2);
                    }
                } else if (Array.isArray(input)) {
                    return new Uint8Array(input);
                }
                throw new Error('Unsupported input type: ' + typeof input);
            },
            bufToString: function(buf, format) {
                if (Array.isArray(buf) || ArrayBuffer.isView(buf)) {
                    var arr = new Uint8Array(buf);
                    switch (format) {
                        case 'hex':
                            return Array.from(arr).reduce(function(str, byte) { return str + byte.toString(16).padStart(2, '0'); }, '');
                        case 'base64':
                            var binary = '';
                            for (var i = 0; i < arr.length; i++) binary += String.fromCharCode(arr[i]);
                            return btoa(binary);
                        case 'binary':
                            return arr;
                        case 'utf8':
                        case 'utf-8':
                        default:
                            var result = '';
                            var idx = 0;
                            while (idx < arr.length) {
                                var byte = arr[idx];
                                if (byte < 128) { result += String.fromCharCode(byte); idx++; }
                                else if (byte >= 192 && byte < 224) { result += String.fromCharCode(((byte & 31) << 6) | (arr[idx+1] & 63)); idx += 2; }
                                else { result += String.fromCharCode(((byte & 15) << 12) | ((arr[idx+1] & 63) << 6) | (arr[idx+2] & 63)); idx += 3; }
                            }
                            return result;
                    }
                }
                throw new Error('Input is not a valid buffer');
            },
        },
    },
    currentScriptInfo: {
        name: '',
        description: '',
        version: '',
        author: '',
        homepage: '',
        rawScript: '',
    },
    version: '2.0.0',
    env: 'mobile',
    };
    return _instance;
})();

globalThis.EVENT_NAMES = {
    request: 'request',
    inited: 'inited'
};

globalThis.setTimeout = function(callback, timeout) {
    if (typeof callback !== 'function') throw new Error('callback required a function');
    return callback();
};
globalThis.clearTimeout = function(id) {};

globalThis.__lxPackages = {};

globalThis.__lxPackages['he'] = {
    encode: function(str) {
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#x27;');
    },
    decode: function(str) {
        if (!str || typeof str !== 'string') return str || '';
        return str
            .replace(/&#x([0-9a-fA-F]+);/g, function(m, hex) { var code = parseInt(hex, 16); return code > 0 ? String.fromCharCode(code) : m; })
            .replace(/&#(\d+);/g, function(m, dec) { var code = parseInt(dec, 10); return code > 0 ? String.fromCharCode(code) : m; })
            .replace(/&nbsp;/g, ' ')
            .replace(/&amp;/g, '&')
            .replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>')
            .replace(/&quot;/g, '"')
            .replace(/&apos;/g, "'")
            .replace(/&#39;/g, "'")
            .replace(/&#x27;/g, "'")
            .replace(/&copy;/g, '\u00A9')
            .replace(/&reg;/g, '\u00AE')
            .replace(/&trade;/g, '\u2122')
            .replace(/&mdash;/g, '\u2014')
            .replace(/&ndash;/g, '\u2013')
            .replace(/&ldquo;/g, '\u201C')
            .replace(/&rdquo;/g, '\u201D')
            .replace(/&lsquo;/g, '\u2018')
            .replace(/&rsquo;/g, '\u2019')
            .replace(/&hellip;/g, '\u2026')
            .replace(/&bull;/g, '\u2022')
            .replace(/&middot;/g, '\u00B7')
            .replace(/&laquo;/g, '\u00AB')
            .replace(/&raquo;/g, '\u00BB')
            .replace(/&times;/g, '\u00D7')
            .replace(/&divide;/g, '\u00F7')
            .replace(/&rarr;/g, '\u2192')
            .replace(/&larr;/g, '\u2190')
            .replace(/&euro;/g, '\u20AC')
            .replace(/&pound;/g, '\u00A3')
            .replace(/&yen;/g, '\u00A5')
            .replace(/&cent;/g, '\u00A2')
            .replace(/&deg;/g, '\u00B0')
            .replace(/&plusmn;/g, '\u00B1')
            .replace(/&para;/g, '\u00B6')
            .replace(/&sect;/g, '\u00A7')
            .replace(/&dagger;/g, '\u2020')
            .replace(/&Dagger;/g, '\u2021')
            .replace(/&permil;/g, '\u2030')
            .replace(/&lsaquo;/g, '\u2039')
            .replace(/&rsaquo;/g, '\u203A')
            .replace(/&spades;/g, '\u2660')
            .replace(/&clubs;/g, '\u2663')
            .replace(/&hearts;/g, '\u2665')
            .replace(/&diams;/g, '\u2666')
            .replace(/&oline;/g, '\u203E')
            .replace(/&lceil;/g, '\u2308')
            .replace(/&rceil;/g, '\u2309')
            .replace(/&lfloor;/g, '\u230A')
            .replace(/&rfloor;/g, '\u230B')
            .replace(/&lang;/g, '\u2329')
            .replace(/&rang;/g, '\u232A')
            .replace(/&loz;/g, '\u25CA')
            .replace(/&\w+;/g, function(m) { return m; });
    },
    escape: function(str) {
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#x27;');
    }
};
Object.defineProperty(globalThis.__lxPackages['he'], 'default', { get: function() { return globalThis.__lxPackages['he']; } });

globalThis.__lxPackages['cheerio'] = (function() {
    function CheerioNode(tagName, attribs, parent) {
        this.tagName = tagName;
        this.attribs = attribs || {};
        this.parent = parent || null;
        this.children = [];
        this.data = '';
    }
    CheerioNode.prototype.getAttribute = function(name) { return this.attribs[name] || null; };
    CheerioNode.prototype.text = function() {
        var result = this.data || '';
        for (var i = 0; i < this.children.length; i++) {
            var child = this.children[i];
            if (child && typeof child.text === 'function') result += child.text();
            else if (typeof child === 'string') result += child;
        }
        return result;
    };
    CheerioNode.prototype.html = function() {
        var result = '';
        for (var i = 0; i < this.children.length; i++) {
            var child = this.children[i];
            if (child && typeof child.html === 'function') result += child.html();
            else if (typeof child === 'string') result += child;
        }
        return result;
    };
    CheerioNode.prototype.attr = function(name) { return this.attribs[name] || null; };
    CheerioNode.prototype.find = function(selector) { return new CheerioSelection([], this); };
    CheerioNode.prototype.each = function(fn) { return this; };
    CheerioNode.prototype.eq = function(i) { return new CheerioSelection([], this); };
    CheerioNode.prototype.first = function() { return new CheerioSelection([], this); };

    function CheerioSelection(elements, root) {
        this.elements = elements || [];
        this.root = root || null;
        this.length = this.elements.length;
    }
    CheerioSelection.prototype.text = function() {
        var result = '';
        for (var i = 0; i < this.elements.length; i++) {
            var el = this.elements[i];
            if (el && typeof el.text === 'function') result += el.text();
            else if (typeof el === 'string') result += el;
        }
        return result;
    };
    CheerioSelection.prototype.html = function() {
        var result = '';
        for (var i = 0; i < this.elements.length; i++) {
            var el = this.elements[i];
            if (el && typeof el.html === 'function') result += el.html();
            else if (typeof el === 'string') result += el;
        }
        return result;
    };
    CheerioSelection.prototype.attr = function(name) {
        if (this.elements.length > 0 && this.elements[0] && typeof this.elements[0].attr === 'function') return this.elements[0].attr(name);
        return null;
    };
    CheerioSelection.prototype.find = function(selector) {
        var results = [];
        for (var i = 0; i < this.elements.length; i++) {
            var el = this.elements[i];
            if (el && typeof el.find === 'function') {
                var found = el.find(selector);
                if (found && found.elements) results = results.concat(found.elements);
            }
        }
        return new CheerioSelection(results, this.root);
    };
    CheerioSelection.prototype.each = function(fn) {
        for (var i = 0; i < this.elements.length; i++) fn.call(this.elements[i], i, this.elements[i]);
        return this;
    };
    CheerioSelection.prototype.eq = function(i) {
        if (i >= 0 && i < this.elements.length) return new CheerioSelection([this.elements[i]], this.root);
        return new CheerioSelection([], this.root);
    };
    CheerioSelection.prototype.first = function() { return this.eq(0); };
    CheerioSelection.prototype.last = function() { return this.eq(this.elements.length - 1); };
    CheerioSelection.prototype.toArray = function() { return this.elements.slice(); };
    CheerioSelection.prototype.map = function(fn) {
        var result = [];
        for (var i = 0; i < this.elements.length; i++) result.push(fn.call(this.elements[i], i, this.elements[i]));
        return new CheerioSelection(result, this.root);
    };
    CheerioSelection.prototype.filter = function(fn) {
        var result = [];
        for (var i = 0; i < this.elements.length; i++) {
            if (typeof fn === 'function') { if (fn.call(this.elements[i], i, this.elements[i])) result.push(this.elements[i]); }
            else if (typeof fn === 'string') { result.push(this.elements[i]); }
        }
        return new CheerioSelection(result, this.root);
    };
    CheerioSelection.prototype.children = function() { return new CheerioSelection([], this.root); };
    CheerioSelection.prototype.parent = function() { return new CheerioSelection([], this.root); };
    CheerioSelection.prototype.siblings = function() { return new CheerioSelection([], this.root); };
    CheerioSelection.prototype.next = function() { return new CheerioSelection([], this.root); };
    CheerioSelection.prototype.prev = function() { return new CheerioSelection([], this.root); };
    CheerioSelection.prototype.is = function() { return false; };
    CheerioSelection.prototype.hasClass = function() { return false; };
    CheerioSelection.prototype.addClass = function() { return this; };
    CheerioSelection.prototype.removeClass = function() { return this; };
    CheerioSelection.prototype.toggleClass = function() { return this; };
    CheerioSelection.prototype.remove = function() { return this; };
    CheerioSelection.prototype.empty = function() { return this; };
    CheerioSelection.prototype.replaceWith = function() { return this; };
    CheerioSelection.prototype.before = function() { return this; };
    CheerioSelection.prototype.after = function() { return this; };
    CheerioSelection.prototype.prepend = function() { return this; };
    CheerioSelection.prototype.append = function() { return this; };
    CheerioSelection.prototype.wrap = function() { return this; };
    CheerioSelection.prototype.unwrap = function() { return this; };
    CheerioSelection.prototype.clone = function() { return new CheerioSelection(this.elements.slice(), this.root); };
    CheerioSelection.prototype.get = function(i) { return i !== undefined ? this.elements[i] : this.elements; };
    CheerioSelection.prototype.data = function(name) { return null; };
    CheerioSelection.prototype.val = function() { return null; };
    CheerioSelection.prototype.prop = function() { return null; };
    CheerioSelection.prototype.css = function() { return this; };
    CheerioSelection.prototype.contents = function() { return new CheerioSelection([], this.root); };

    function parseHTML(html) {
        if (typeof html !== 'string') return { root: new CheerioNode('root'), idMap: {}, tagMap: {} };
        var idMap = {};
        var tagMap = {};
        var root = new CheerioNode('root');
        var stack = [root];
        var tagRe = /<(\/?)([a-zA-Z][a-zA-Z0-9]*)((?:\s+[^>]*?)?)(\/?)>/g;
        var lastIdx = 0;
        var match;
        while ((match = tagRe.exec(html)) !== null) {
            if (match.index > lastIdx) {
                var textContent = html.substring(lastIdx, match.index);
                if (textContent.trim()) {
                    var textNode = new CheerioNode('text', null, stack[stack.length - 1]);
                    textNode.data = textContent.replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&#x27;/g,"'");
                    stack[stack.length - 1].children.push(textNode);
                }
            }
            var isClosing = match[1] === '/';
            var tagName = match[2].toLowerCase();
            var attrStr = match[3];
            var isSelfClosing = match[4] === '/';
            if (isClosing) {
                if (stack.length > 1) {
                    var popped = stack.pop();
                    if (popped.tagName !== tagName) stack.push(popped);
                }
            } else {
                var attribs = {};
                var attrRe = /([a-zA-Z_:][-a-zA-Z0-9_.:]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+)))?/g;
                var attrMatch;
                while ((attrMatch = attrRe.exec(attrStr)) !== null) {
                    var attrName = attrMatch[1];
                    var attrVal = attrMatch[2] !== undefined ? attrMatch[2] : (attrMatch[3] !== undefined ? attrMatch[3] : (attrMatch[4] !== undefined ? attrMatch[4] : ''));
                    attribs[attrName] = attrVal;
                }
                var node = new CheerioNode(tagName, attribs, stack[stack.length - 1]);
                stack[stack.length - 1].children.push(node);
                if (attribs.id) idMap[attribs.id] = node;
                if (!tagMap[tagName]) tagMap[tagName] = [];
                tagMap[tagName].push(node);
                var voidElements = ['area','base','br','col','embed','hr','img','input','link','meta','param','source','track','wbr'];
                if (!isSelfClosing && voidElements.indexOf(tagName) === -1) stack.push(node);
            }
            lastIdx = tagRe.lastIndex;
        }
        if (lastIdx < html.length) {
            var remaining = html.substring(lastIdx);
            if (remaining.trim()) {
                var textNode = new CheerioNode('text', null, stack[stack.length - 1]);
                textNode.data = remaining.replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&#x27;/g,"'");
                stack[stack.length - 1].children.push(textNode);
            }
        }
        return { root: root, idMap: idMap, tagMap: tagMap };
    }

    function load(html) {
        var parsed = parseHTML(html);
        var selector = function(sel) {
            if (!sel) return new CheerioSelection([parsed.root], parsed.root);
            if (sel.charAt(0) === '#') {
                var id = sel.substring(1);
                var el = parsed.idMap[id];
                return new CheerioSelection(el ? [el] : [], parsed.root);
            }
            if (sel.charAt(0) === '.') {
                var results = [];
                (function findClass(node) {
                    if (node.attribs && node.attribs['class']) {
                        var classes = node.attribs['class'].split(/\s+/);
                        for (var c = 0; c < classes.length; c++) {
                            if (classes[c] === sel.substring(1)) { results.push(node); break; }
                        }
                    }
                    if (node.children) for (var i = 0; i < node.children.length; i++) findClass(node.children[i]);
                })(parsed.root);
                return new CheerioSelection(results, parsed.root);
            }
            var tagName = sel.toLowerCase();
            var elements = parsed.tagMap[tagName] || [];
            return new CheerioSelection(elements.slice(), parsed.root);
        };
        selector.root = parsed.root;
        selector.html = function() { return html || ''; };
        selector.text = function() { return parsed.root.text(); };
        selector.find = function(sel) { return selector(sel); };
        selector.prototype = CheerioSelection.prototype;
        return selector;
    }

    return { load: load };
})();
Object.defineProperty(globalThis.__lxPackages['cheerio'], 'default', { get: function() { return globalThis.__lxPackages['cheerio']; } });

globalThis.__lxPackages['crypto-js'] = (function() {
    var md5Chars = '0123456789abcdef';
    function leftRotate(n, s) { return ((n << s) | (n >>> (32 - s))) >>> 0; }
    function addUnsigned(x, y) { return (x + y) >>> 0; }
    function F(x,y,z){return(x&y)|((~x)&z);}function G(x,y,z){return(x&z)|(y&(~z));}function H(x,y,z){return x^y^z;}function I(x,y,z){return y^(x|(~z));}
    var T = [0xd76aa478,0xe8c7b756,0x242070db,0xc1bdceee,0xf57c0faf,0x4787c62a,0xa8304613,0xfd469501,0x698098d8,0x8b44f7af,0xffff5bb1,0x895cd7be,0x6b901122,0xfd987193,0xa679438e,0x49b40821,
        0xf61e2562,0xc040b340,0x265e5a51,0xe9b6c7aa,0xd62f105d,0x02441453,0xd8a1e681,0xe7d3fbc8,0x21e1cde6,0xc33707d6,0xf4d50d87,0x455a14ed,0xa9e3e905,0xfcefa3f8,0x676f02d9,0x8d2a4c8a,
        0xfffa3942,0x8771f681,0x6d9d6122,0xfde5380c,0xa4beea44,0x4bdecfa9,0xf6bb4b60,0xbebfbc70,0x289b7ec6,0xeaa127fa,0xd4ef3085,0x04881d05,0xd9d4d039,0xe6db99e9,0x1fa27cf8,
        0xc4ac5665,0xf4292244,0x432aff97,0xab9423a7,0xfc93a039,0x655b59c3,0x8f0ccc92,0xffeff47d,0x85845dd1,0x6fa87e4f,0xfe2ce6e0,0xa3014314,0x4e0811a1,0xf7537e82,0xbd3af235,
        0x2ad7d2bb,0xeb86d391];
    var S11=7,S12=12,S13=17,S14=22,S21=5,S22=9,S23=14,S24=20,S31=4,S32=11,S33=16,S34=23,S41=6,S42=10,S43=15,S44=21;
    function FF(a,b,c,d,x,s,ac){a=addUnsigned(a,addUnsigned(addUnsigned(F(b,c,d),x),ac));return addUnsigned(leftRotate(a,s),b);}
    function GG(a,b,c,d,x,s,ac){a=addUnsigned(a,addUnsigned(addUnsigned(G(b,c,d),x),ac));return addUnsigned(leftRotate(a,s),b);}
    function HH(a,b,c,d,x,s,ac){a=addUnsigned(a,addUnsigned(addUnsigned(H(b,c,d),x),ac));return addUnsigned(leftRotate(a,s),b);}
    function II(a,b,c,d,x,s,ac){a=addUnsigned(a,addUnsigned(addUnsigned(I(b,c,d),x),ac));return addUnsigned(leftRotate(a,s),b);}
    function cvtHex(n){var h='';for(var j=0;j<=3;j++){var v=(n>>(j*8))&255;h+=md5Chars.charAt((v>>>4)&0xf)+md5Chars.charAt(v&0xf);}return h;}

    function computeMD5(str) {
        str = String(str);
        var len=str.length*8;str+=String.fromCharCode(128);while(str.length%64!==56){str+=String.fromCharCode(0);}
        str+=String.fromCharCode(len&0xff,(len>>8)&0xff,(len>>16)&0xff,(len>>24)&0xff,0,0,0,0);
        var words=[];for(var i=0;i<str.length;i+=4){words.push(str.charCodeAt(i)|(str.charCodeAt(i+1)<<8)|(str.charCodeAt(i+2)<<16)|(str.charCodeAt(i+3)<<24));}
        var a=0x67452301,b=0xefcdab89,c=0x98badcfe,d=0x10325476;
        for(var off=0;off<words.length;off+=16){
            var aa=a,bb=b,cc=c,dd=d;
            a=FF(a,b,c,d,words[off],S11,T[0]);d=FF(d,a,b,c,words[off+1],S12,T[1]);c=FF(c,d,a,b,words[off+2],S13,T[2]);b=FF(b,c,d,a,words[off+3],S14,T[3]);
            a=FF(a,b,c,d,words[off+4],S11,T[4]);d=FF(d,a,b,c,words[off+5],S12,T[5]);c=FF(c,d,a,b,words[off+6],S13,T[6]);b=FF(b,c,d,a,words[off+7],S14,T[7]);
            a=FF(a,b,c,d,words[off+8],S11,T[8]);d=FF(d,a,b,c,words[off+9],S12,T[9]);c=FF(c,d,a,b,words[off+10],S13,T[10]);b=FF(b,c,d,a,words[off+11],S14,T[11]);
            a=FF(a,b,c,d,words[off+12],S11,T[12]);d=FF(d,a,b,c,words[off+13],S12,T[13]);c=FF(c,d,a,b,words[off+14],S13,T[14]);b=FF(b,c,d,a,words[off+15],S14,T[15]);
            a=GG(a,b,c,d,words[off+1],S21,T[16]);d=GG(d,a,b,c,words[off+6],S22,T[17]);c=GG(c,d,a,b,words[off+11],S23,T[18]);b=GG(b,c,d,a,words[off],S24,T[19]);
            a=GG(a,b,c,d,words[off+5],S21,T[20]);d=GG(d,a,b,c,words[off+10],S22,T[21]);c=GG(c,d,a,b,words[off+15],S23,T[22]);b=GG(b,c,d,a,words[off+4],S24,T[23]);
            a=GG(a,b,c,d,words[off+9],S21,T[24]);d=GG(d,a,b,c,words[off+14],S22,T[25]);c=GG(c,d,a,b,words[off+3],S23,T[26]);b=GG(b,c,d,a,words[off+8],S24,T[27]);
            a=GG(a,b,c,d,words[off+13],S21,T[28]);d=GG(d,a,b,c,words[off+2],S22,T[29]);c=GG(c,d,a,b,words[off+7],S23,T[30]);b=GG(b,c,d,a,words[off+12],S24,T[31]);
            a=HH(a,b,c,d,words[off+5],S31,T[32]);d=HH(d,a,b,c,words[off+8],S32,T[33]);c=HH(c,d,a,b,words[off+11],S33,T[34]);b=HH(b,c,d,a,words[off+14],S34,T[35]);
            a=HH(a,b,c,d,words[off+1],S31,T[36]);d=HH(d,a,b,c,words[off+4],S32,T[37]);c=HH(c,d,a,b,words[off+7],S33,T[38]);b=HH(b,c,d,a,words[off+10],S34,T[39]);
            a=HH(a,b,c,d,words[off+13],S31,T[40]);d=HH(d,a,b,c,words[off],S32,T[41]);c=HH(c,d,a,b,words[off+3],S33,T[42]);b=HH(b,c,d,a,words[off+6],S34,T[43]);
            a=HH(a,b,c,d,words[off+9],S31,T[44]);d=HH(d,a,b,c,words[off+12],S32,T[45]);c=HH(c,d,a,b,words[off+15],S33,T[46]);b=HH(b,c,d,a,words[off+2],S34,T[47]);
            a=II(a,b,c,d,words[off],S41,T[48]);d=II(d,a,b,c,words[off+7],S42,T[49]);c=II(c,d,a,b,words[off+14],S43,T[50]);b=II(b,c,d,a,words[off+5],S44,T[51]);
            a=II(a,b,c,d,words[off+12],S41,T[52]);d=II(d,a,b,c,words[off+3],S42,T[53]);c=II(c,d,a,b,words[off+10],S43,T[54]);b=II(b,c,d,a,words[off+1],S44,T[55]);
            a=II(a,b,c,d,words[off+8],S41,T[56]);d=II(d,a,b,c,words[off+15],S42,T[57]);c=II(c,d,a,b,words[off+6],S43,T[58]);b=II(b,c,d,a,words[off+13],S44,T[59]);
            a=II(a,b,c,d,words[off+4],S41,T[60]);d=II(d,a,b,c,words[off+11],S42,T[61]);c=II(c,d,a,b,words[off+2],S43,T[62]);b=II(b,c,d,a,words[off+9],S44,T[63]);
            a=addUnsigned(a,aa);b=addUnsigned(b,bb);c=addUnsigned(c,cc);d=addUnsigned(d,dd);
        }
        return cvtHex(a)+cvtHex(b)+cvtHex(c)+cvtHex(d);
    }

    function strToWords(str) {
        var words = [];
        for (var i = 0; i < str.length; i++) {
            var c = str.charCodeAt(i);
            words[i >>> 2] |= c << (24 - (i % 4) * 8);
            words[i >>> 2] = words[i >>> 2] >>> 0;
        }
        return words;
    }

    function wordsToHex(words) {
        var hex = '';
        for (var i = 0; i < words.length * 4; i++) {
            var b = (words[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xff;
            hex += ((b >>> 4) & 0xf).toString(16) + (b & 0xf).toString(16);
        }
        return hex;
    }

    function sha256Core(messageWords) {
        var H = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
        var K = [0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
            0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
            0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
            0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
            0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
            0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
            0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
            0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];
        function rotr(x, n) { return ((x >>> n) | (x << (32 - n))) >>> 0; }
        function ch(x,y,z) { return ((x & y) ^ ((~x) & z)) >>> 0; }
        function maj(x,y,z) { return ((x & y) ^ (x & z) ^ (y & z)) >>> 0; }
        function sigma0(x) { return (rotr(x,2) ^ rotr(x,13) ^ rotr(x,22)) >>> 0; }
        function sigma1(x) { return (rotr(x,6) ^ rotr(x,11) ^ rotr(x,25)) >>> 0; }
        function gamma0(x) { return (rotr(x,7) ^ rotr(x,18) ^ (x >>> 3)) >>> 0; }
        function gamma1(x) { return (rotr(x,17) ^ rotr(x,19) ^ (x >>> 10)) >>> 0; }
        var W = [];
        for (var i = 0; i < 64; i++) {
            if (i < 16) { W[i] = messageWords[i] >>> 0; }
            else { W[i] = (gamma1(W[i-2]) + W[i-7] + gamma0(W[i-15]) + W[i-16]) >>> 0; }
        }
        var a = H[0], b = H[1], c = H[2], d = H[3], e = H[4], f = H[5], g = H[6], h = H[7];
        for (var i = 0; i < 64; i++) {
            var T1 = (h + sigma1(e) + ch(e,f,g) + K[i] + W[i]) >>> 0;
            var T2 = (sigma0(a) + maj(a,b,c)) >>> 0;
            h = g; g = f; f = e; e = (d + T1) >>> 0; d = c; c = b; b = a; a = (T1 + T2) >>> 0;
        }
        H[0] = (H[0] + a) >>> 0; H[1] = (H[1] + b) >>> 0; H[2] = (H[2] + c) >>> 0; H[3] = (H[3] + d) >>> 0;
        H[4] = (H[4] + e) >>> 0; H[5] = (H[5] + f) >>> 0; H[6] = (H[6] + g) >>> 0; H[7] = (H[7] + h) >>> 0;
        return H;
    }

    function computeSHA256(str) {
        str = String(str);
        var msgLen = str.length;
        var bitLen = msgLen * 8;
        str += '\x80';
        while (str.length % 64 !== 56) str += '\x00';
        var lenWords = [];
        for (var i = 56; i >= 0; i -= 8) {
            lenWords.push((bitLen >>> i) & 0xff);
        }
        for (var i = 0; i < lenWords.length; i++) {
            str += String.fromCharCode(lenWords[i]);
        }
        var words = [];
        for (var i = 0; i < str.length; i += 4) {
            words.push(
                ((str.charCodeAt(i) & 0xff) << 24) |
                ((str.charCodeAt(i+1) & 0xff) << 16) |
                ((str.charCodeAt(i+2) & 0xff) << 8) |
                (str.charCodeAt(i+3) & 0xff)
            );
        }
        var H = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
        for (var blk = 0; blk < words.length; blk += 16) {
            var block = words.slice(blk, blk + 16);
            var result = sha256Core(block);
            for (var j = 0; j < 8; j++) H[j] = (H[j] + result[j]) >>> 0;
        }
        return wordsToHex(H);
    }

    function computeHmacSHA256(key, message) {
        key = String(key);
        message = String(message);
        if (key.length > 64) key = computeSHA256(key);
        while (key.length < 64) key += '\x00';
        var oKeyPad = '', iKeyPad = '';
        for (var i = 0; i < 64; i++) {
            var k = key.charCodeAt(i);
            oKeyPad += String.fromCharCode(k ^ 0x5c);
            iKeyPad += String.fromCharCode(k ^ 0x36);
        }
        var innerHash = computeSHA256(iKeyPad + message);
        var innerBytes = '';
        for (var i = 0; i < innerHash.length; i += 2) {
            innerBytes += String.fromCharCode(parseInt(innerHash.substr(i, 2), 16));
        }
        return computeSHA256(oKeyPad + innerBytes);
    }

    function CryptoJSWordArray(words, sigBytes) {
        this.words = words || [];
        this.sigBytes = sigBytes !== undefined ? sigBytes : (this.words ? this.words.length * 4 : 0);
    }
    CryptoJSWordArray.prototype.toString = function(encoder) {
        if (encoder && encoder === Hex) return wordsToHex(this.words).substring(0, this.sigBytes * 2);
        return wordsToHex(this.words).substring(0, this.sigBytes * 2);
    };
    CryptoJSWordArray.prototype.clone = function() {
        return new CryptoJSWordArray(this.words.slice(), this.sigBytes);
    };

    var Hex = {
        stringify: function(wordArray) {
            return wordsToHex(wordArray.words).substring(0, wordArray.sigBytes * 2);
        }
    };
    var Base64 = {
        stringify: function(wordArray) {
            var hex = wordsToHex(wordArray.words).substring(0, wordArray.sigBytes * 2);
            var binary = '';
            for (var i = 0; i < hex.length; i += 2) binary += String.fromCharCode(parseInt(hex.substr(i, 2), 16));
            try { return btoa(binary); } catch(e) { return ''; }
        }
    };
    var Utf8 = {
        stringify: function(wordArray) {
            var hex = wordsToHex(wordArray.words).substring(0, wordArray.sigBytes * 2);
            var str = '';
            for (var i = 0; i < hex.length; i += 2) str += String.fromCharCode(parseInt(hex.substr(i, 2), 16));
            return str;
        },
        parse: function(str) {
            var words = strToWords(str);
            return new CryptoJSWordArray(words, str.length);
        }
    };

    function hashToWordArray(hexStr) {
        var words = [];
        for (var i = 0; i < hexStr.length; i += 8) {
            words.push(parseInt(hexStr.substr(i, 8), 16) >>> 0);
        }
        return new CryptoJSWordArray(words, hexStr.length / 2);
    }

    // ── AES-128/192/256-ECB implementation ──
    var AES_SBOX = [
        0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
        0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
        0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
        0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
        0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
        0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
        0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
        0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
        0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
        0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
        0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
        0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
        0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
        0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
        0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
        0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
    ];
    var AES_RCON = [0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36];

    function aesSubBytes(s) {
        for (var i = 0; i < 16; i++) s[i] = AES_SBOX[s[i]];
    }
    function aesShiftRows(s) {
        var t;
        t=s[1];s[1]=s[5];s[5]=s[9];s[9]=s[13];s[13]=t;
        t=s[2];s[2]=s[10];s[10]=t;t=s[6];s[6]=s[14];s[14]=t;
        t=s[3];s[3]=s[15];s[15]=s[11];s[11]=s[7];s[7]=t;
    }
    function aesMixColumns(s) {
        for (var i = 0; i < 4; i++) {
            var a=s[i*4],b=s[i*4+1],c=s[i*4+2],d=s[i*4+3];
            s[i*4]  =aesGmul(2,a)^aesGmul(3,b)^c^d;
            s[i*4+1]=a^aesGmul(2,b)^aesGmul(3,c)^d;
            s[i*4+2]=a^b^aesGmul(2,c)^aesGmul(3,d);
            s[i*4+3]=aesGmul(3,a)^b^c^aesGmul(2,d);
        }
    }
    function aesGmul(a, b) {
        var p = 0;
        for (var i = 0; i < 8; i++) {
            if (b & 1) p ^= a;
            var hi = a & 0x80;
            a = (a << 1) & 0xFF;
            if (hi) a ^= 0x1b;
            b >>= 1;
        }
        return p;
    }
    function aesAddRoundKey(s, rk) {
        for (var i = 0; i < 16; i++) s[i] ^= rk[i];
    }
    function aesKeyExpansion(key) {
        // key is byte array (16/24/32 bytes)
        var nk = key.length / 4;
        var nr = nk + 6;
        var ws = (nr + 1) * 16;
        var w = new Array(ws);
        var i;
        for (i = 0; i < nk * 4; i++) w[i] = key[i];
        for (i = nk; i < (nr + 1) * 4; i++) {
            var t = [w[(i-1)*4], w[(i-1)*4+1], w[(i-1)*4+2], w[(i-1)*4+3]];
            if (i % nk === 0) {
                var tmp = t[0]; t[0]=AES_SBOX[t[1]]^AES_RCON[i/nk-1]; t[1]=AES_SBOX[t[2]]; t[2]=AES_SBOX[t[3]]; t[3]=AES_SBOX[tmp];
            } else if (nk > 6 && i % nk === 4) {
                t[0]=AES_SBOX[t[0]]; t[1]=AES_SBOX[t[1]]; t[2]=AES_SBOX[t[2]]; t[3]=AES_SBOX[t[3]];
            }
            w[i*4]=w[(i-nk)*4]^t[0]; w[i*4+1]=w[(i-nk)*4+1]^t[1]; w[i*4+2]=w[(i-nk)*4+2]^t[2]; w[i*4+3]=w[(i-nk)*4+3]^t[3];
        }
        return w;
    }
    function aesEncryptBlock(block, w, nr) {
        var s = block.slice();
        aesAddRoundKey(s, w.slice(0, 16));
        for (var r = 1; r < nr; r++) {
            aesSubBytes(s);
            aesShiftRows(s);
            aesMixColumns(s);
            aesAddRoundKey(s, w.slice(r * 16, r * 16 + 16));
        }
        aesSubBytes(s);
        aesShiftRows(s);
        aesAddRoundKey(s, w.slice(nr * 16, nr * 16 + 16));
        return s;
    }
    function wordsToBytes(words, sigBytes) {
        var bytes = [];
        for (var i = 0; i < sigBytes; i++) {
            bytes.push((words[i >>> 2] >>> (24 - (i % 4) * 8)) & 0xFF);
        }
        return bytes;
    }
    function bytesToWords(bytes) {
        var words = [];
        for (var i = 0; i < bytes.length; i++) {
            words[i >>> 2] |= (bytes[i] & 0xFF) << (24 - (i % 4) * 8);
            words[i >>> 2] = words[i >>> 2] >>> 0;
        }
        return words;
    }
    function aesEcbEncrypt(plainWords, keyWords, keySigBytes) {
        var keyBytes = wordsToBytes(keyWords, keySigBytes);
        var w = aesKeyExpansion(keyBytes);
        var nr = keyBytes.length / 4 + 6;
        var resultWords = [];
        for (var b = 0; b < plainWords.length; b += 4) {
            var blockBytes = wordsToBytes(plainWords.slice(b, b + 4), 16);
            var enc = aesEncryptBlock(blockBytes, w, nr);
            var encWords = bytesToWords(enc);
            for (var j = 0; j < encWords.length; j++) resultWords.push(encWords[j]);
        }
        return resultWords;
    }
    function aesEcbDecrypt(cipherWords, keyWords, keySigBytes) {
        var keyBytes = wordsToBytes(keyWords, keySigBytes);
        var w = aesKeyExpansion(keyBytes);
        var nr = keyBytes.length / 4 + 6;
        // Inverse S-box
        var INV_SBOX = new Array(256);
        for (var i = 0; i < 256; i++) INV_SBOX[AES_SBOX[i]] = i;
        function invSubBytes(s) { for (var i = 0; i < 16; i++) s[i] = INV_SBOX[s[i]]; }
        function invShiftRows(s) {
            var t;
            t=s[13];s[13]=s[9];s[9]=s[5];s[5]=s[1];s[1]=t;
            t=s[2];s[2]=s[10];s[10]=t;t=s[6];s[6]=s[14];s[14]=t;
            t=s[3];s[3]=s[7];s[7]=s[11];s[11]=s[15];s[15]=t;
        }
        function invMixColumns(s) {
            for (var i = 0; i < 4; i++) {
                var a=s[i*4],b=s[i*4+1],c=s[i*4+2],d=s[i*4+3];
                s[i*4]  =aesGmul(14,a)^aesGmul(11,b)^aesGmul(13,c)^aesGmul(9,d);
                s[i*4+1]=aesGmul(9,a)^aesGmul(14,b)^aesGmul(11,c)^aesGmul(13,d);
                s[i*4+2]=aesGmul(13,a)^aesGmul(9,b)^aesGmul(14,c)^aesGmul(11,d);
                s[i*4+3]=aesGmul(11,a)^aesGmul(13,b)^aesGmul(9,c)^aesGmul(14,d);
            }
        }
        var resultWords = [];
        for (var b = 0; b < cipherWords.length; b += 4) {
            var blockBytes = wordsToBytes(cipherWords.slice(b, b + 4), 16);
            var s = blockBytes.slice();
            aesAddRoundKey(s, w.slice(nr * 16, nr * 16 + 16));
            for (var r = nr - 1; r >= 1; r--) {
                invShiftRows(s);
                invSubBytes(s);
                aesAddRoundKey(s, w.slice(r * 16, r * 16 + 16));
                invMixColumns(s);
            }
            invShiftRows(s);
            invSubBytes(s);
            aesAddRoundKey(s, w.slice(0, 16));
            var decWords = bytesToWords(s);
            for (var j = 0; j < decWords.length; j++) resultWords.push(decWords[j]);
        }
        return resultWords;
    }

    return {
        MD5: function(message) {
            var hash = computeMD5(message);
            return hashToWordArray(hash);
        },
        SHA256: function(message) {
            var hash = computeSHA256(message);
            return hashToWordArray(hash);
        },
        HmacSHA256: function(message, key) {
            var hash = computeHmacSHA256(key, message);
            return hashToWordArray(hash);
        },
        SHA1: function(message) {
            return hashToWordArray('');
        },
        SHA512: function(message) {
            return hashToWordArray('');
        },
        HmacMD5: function(message, key) {
            return hashToWordArray('');
        },
        enc: { Hex: Hex, Base64: Base64, Utf8: Utf8, Latin1: Utf8 },
        AES: {
            encrypt: function(message, key, cfg) {
                // 支持 message 为 WordArray 或 string
                var msgWords;
                var msgSigBytes;
                if (message && typeof message === 'object' && message.words) {
                    msgWords = message.words;
                    msgSigBytes = message.sigBytes;
                } else {
                    var parsed = Utf8.parse(String(message || ''));
                    msgWords = parsed.words;
                    msgSigBytes = parsed.sigBytes;
                }
                // 支持 key 为 WordArray 或 string
                var keyWords;
                var keySigBytes;
                if (key && typeof key === 'object' && key.words) {
                    keyWords = key.words;
                    keySigBytes = key.sigBytes;
                } else {
                    var parsedKey = Utf8.parse(String(key || ''));
                    keyWords = parsedKey.words;
                    keySigBytes = parsedKey.sigBytes;
                }
                // PKCS7 padding
                var blockSize = 4; // 16 bytes = 4 words
                var padLen = blockSize - (msgSigBytes % (blockSize * 4)) / 4;
                if (padLen === 0) padLen = blockSize;
                var paddedWords = msgWords.slice(0, Math.ceil(msgSigBytes / 4));
                // 补齐最后一个word的零头
                var remainder = msgSigBytes % 4;
                if (remainder > 0 && paddedWords.length > 0) {
                    var mask = 0xFFFFFFFF << (32 - remainder * 8);
                    paddedWords[paddedWords.length - 1] = paddedWords[paddedWords.length - 1] & mask;
                }
                for (var p = 0; p < padLen; p++) {
                    var idx = paddedWords.length;
                    var bytePos = (msgSigBytes + p) % 4;
                    var wordIdx = (msgSigBytes + p) >>> 2;
                    if (wordIdx >= paddedWords.length) paddedWords.push(0);
                    paddedWords[wordIdx] |= (padLen << (24 - bytePos * 8));
                }
                // AES-ECB encrypt each 16-byte block
                var encWords = aesEcbEncrypt(paddedWords, keyWords, keySigBytes);
                var encSigBytes = paddedWords.length * 4;
                var cipherWA = new CryptoJSWordArray(encWords, encSigBytes);
                return {
                    toString: function(encoder) {
                        if (encoder && encoder === Base64) return Base64.stringify(cipherWA);
                        return Base64.stringify(cipherWA);
                    },
                    ciphertext: cipherWA
                };
            },
            decrypt: function(ciphertext, key, cfg) {
                // 支持 ciphertext 为 CipherParams 或 Base64 string
                var cipherWords;
                var cipherSigBytes;
                if (typeof ciphertext === 'string') {
                    var parsed = Base64.parse(ciphertext);
                    cipherWords = parsed.words;
                    cipherSigBytes = parsed.sigBytes;
                } else if (ciphertext && ciphertext.ciphertext) {
                    cipherWords = ciphertext.ciphertext.words;
                    cipherSigBytes = ciphertext.ciphertext.sigBytes;
                } else if (ciphertext && ciphertext.words) {
                    cipherWords = ciphertext.words;
                    cipherSigBytes = ciphertext.sigBytes;
                } else {
                    return { toString: function() { return ''; }, plaintext: '' };
                }
                // key
                var keyWords;
                var keySigBytes;
                if (key && typeof key === 'object' && key.words) {
                    keyWords = key.words;
                    keySigBytes = key.sigBytes;
                } else {
                    var parsedKey = Utf8.parse(String(key || ''));
                    keyWords = parsedKey.words;
                    keySigBytes = parsedKey.sigBytes;
                }
                // AES-ECB decrypt
                var decWords = aesEcbDecrypt(cipherWords, keyWords, keySigBytes);
                // PKCS7 unpad
                var padVal = (decWords[decWords.length - 1]) & 0xFF;
                if (padVal > 0 && padVal <= 16) {
                    decWords = decWords.slice(0, decWords.length - (padVal / 4 | 0) + (padVal % 4 === 0 ? 0 : 0));
                }
                var decSigBytes = decWords.length * 4 - padVal;
                var plainWA = new CryptoJSWordArray(decWords, decSigBytes > 0 ? decSigBytes : 0);
                return {
                    toString: function(encoder) {
                        if (encoder && encoder === Utf8) return Utf8.stringify(plainWA);
                        return Utf8.stringify(plainWA);
                    },
                    plaintext: Utf8.stringify(plainWA)
                };
            }
        },
        lib: {
            WordArray: CryptoJSWordArray,
            Word: { create: function() { return new CryptoJSWordArray(); } }
        },
        mode: { ECB: function() {}, CBC: function() {} },
        pad: { Pkcs7: { pad: function(){}, unpad: function(){} } },
        algo: { AES: {}, SHA256: {}, MD5: {} }
    };
})();
Object.defineProperty(globalThis.__lxPackages['crypto-js'], 'default', { get: function() { return globalThis.__lxPackages['crypto-js']; } });

globalThis.__buildAxiosUrl = function(url, config) {
    config = config || {};
    if (typeof url !== 'string') url = (url && url.url) ? url.url : String(url || '');
    var finalUrl = url;
    if (config.baseURL && typeof url === 'string' && url.charAt(0) === '/') {
        finalUrl = config.baseURL + url;
    } else if (config.baseURL && typeof url === 'string' && !/^https?:\/\//i.test(url)) {
        finalUrl = config.baseURL + '/' + url;
    }
    if (config.params && typeof config.params === 'object') {
        var qs = Object.keys(config.params).map(function(k) {
            return encodeURIComponent(k) + '=' + encodeURIComponent(config.params[k]);
        }).join('&');
        finalUrl += (finalUrl.indexOf('?') === -1 ? '?' : '&') + qs;
    }
    return finalUrl;
};

globalThis.__lxPackages['axios'] = function(urlOrConfig, config) {
    try {
        if (typeof urlOrConfig === 'object' && urlOrConfig !== null) {
            config = urlOrConfig;
            urlOrConfig = config.url || config.uri || '';
        }
        config = config || {};
        if (config.data && !config.body) {
            config.body = typeof config.data === 'object' ? JSON.stringify(config.data) : String(config.data);
        }
        if (config.method) {
            config.method = String(config.method).toUpperCase();
        }
        var finalUrl = globalThis.__buildAxiosUrl(urlOrConfig, config);
        if (typeof finalUrl !== 'string') finalUrl = String(finalUrl);
        __nativeLog('[AXIOS] call: ' + (config.method || 'GET') + ' ' + (finalUrl ? finalUrl.substring(0, 100) : ''));
        return globalThis.fetch(finalUrl, config).then(function(resp) {
            __nativeLog('[AXIOS] response: status=' + resp.status + ' hasData=' + (resp.data !== undefined));
            return globalThis.__toAxiosResponse(resp, finalUrl);
        }).catch(function(e) {
            __nativeLog('[AXIOS] call error: ' + e.message);
            var err = e;
            if (!err.response) {
                err.response = { data: null, status: 0, statusText: e.message || 'Network Error', headers: {} };
            }
            throw err;
        });
    } catch(e) {
        __nativeLog('[AXIOS] sync error: ' + e.message);
        return Promise.reject(e);
    }
};
globalThis.__lxPackages['axios'].get = function(url, config) {
    try {
        config = config || {};
        var finalUrl = globalThis.__buildAxiosUrl(url, config);
        if (typeof finalUrl !== 'string') finalUrl = String(finalUrl);
        __nativeLog('[AXIOS] get: ' + (finalUrl ? finalUrl.substring(0, 100) : ''));
        return globalThis.fetch(finalUrl, config).then(function(resp) {
            __nativeLog('[AXIOS] get response: status=' + resp.status + ' bodyLen=' + (resp.body ? resp.body.length : 0));
            return globalThis.__toAxiosResponse(resp, finalUrl);
        }).catch(function(e) {
            __nativeLog('[AXIOS] get error: ' + e.message);
            var err = e;
            if (!err.response) {
                err.response = { data: null, status: 0, statusText: e.message || 'Network Error', headers: {} };
            }
            throw err;
        });
    } catch(e) {
        __nativeLog('[AXIOS] get sync error: ' + e.message);
        return Promise.reject(e);
    }
};
globalThis.__lxPackages['axios'].post = function(url, data, config) {
    try {
        config = config || {};
        config.method = 'POST';
        config.body = typeof data === 'object' ? JSON.stringify(data) : String(data || '');
        var finalUrl = globalThis.__buildAxiosUrl(url, config);
        __nativeLog('[AXIOS] post: ' + (finalUrl ? finalUrl.substring(0, 100) : ''));
        return globalThis.fetch(finalUrl, config).then(function(resp) {
            return globalThis.__toAxiosResponse(resp, finalUrl);
        }).catch(function(e) {
            __nativeLog('[AXIOS] post error: ' + e.message);
            var err = e;
            if (!err.response) { err.response = { data: null, status: 0, statusText: e.message || 'Network Error', headers: {} }; }
            throw err;
        });
    } catch(e) {
        __nativeLog('[AXIOS] post sync error: ' + e.message);
        return Promise.reject(e);
    }
};
globalThis.__lxPackages['axios'].put = function(url, data, config) {
    try {
        config = config || {};
        config.method = 'PUT';
        config.body = typeof data === 'object' ? JSON.stringify(data) : String(data || '');
        var finalUrl = globalThis.__buildAxiosUrl(url, config);
        return globalThis.fetch(finalUrl, config).then(function(resp) {
            return globalThis.__toAxiosResponse(resp, finalUrl);
        }).catch(function(e) {
            var err = e;
            if (!err.response) { err.response = { data: null, status: 0, statusText: e.message || 'Network Error', headers: {} }; }
            throw err;
        });
    } catch(e) { return Promise.reject(e); }
};
globalThis.__lxPackages['axios'].delete = function(url, config) {
    try {
        config = config || {};
        config.method = 'DELETE';
        var finalUrl = globalThis.__buildAxiosUrl(url, config);
        return globalThis.fetch(finalUrl, config).then(function(resp) {
            return globalThis.__toAxiosResponse(resp, finalUrl);
        }).catch(function(e) {
            var err = e;
            if (!err.response) { err.response = { data: null, status: 0, statusText: e.message || 'Network Error', headers: {} }; }
            throw err;
        });
    } catch(e) { return Promise.reject(e); }
};
globalThis.__lxPackages['axios'].create = function(instanceConfig) {
    var baseConfig = instanceConfig || {};
    var instance = function(url, config) {
        config = config || {};
        var merged = {};
        if (baseConfig.headers) merged.headers = baseConfig.headers;
        if (config.headers) { if (!merged.headers) merged.headers = {}; Object.keys(config.headers).forEach(function(k) { merged.headers[k] = config.headers[k]; }); }
        if (baseConfig.baseURL) merged.baseURL = baseConfig.baseURL;
        merged.method = config.method;
        merged.body = config.body;
        merged.params = config.params || baseConfig.params;
        var finalUrl = globalThis.__buildAxiosUrl(url, merged);
        __nativeLog('[AXIOS_INSTANCE] call: ' + (merged.method || 'GET') + ' ' + (finalUrl ? finalUrl.substring(0, 100) : ''));
        return globalThis.fetch(finalUrl, merged).then(function(resp) {
            return globalThis.__toAxiosResponse(resp, finalUrl);
        }).catch(function(e) {
            var err = e;
            if (!err.response) { err.response = { data: null, status: 0, statusText: e.message || 'Network Error', headers: {} }; }
            throw err;
        });
    };
    instance.get = function(url, config) {
        config = config || {};
        var merged = { baseURL: baseConfig.baseURL, params: config.params || baseConfig.params, headers: config.headers || baseConfig.headers };
        var finalUrl = globalThis.__buildAxiosUrl(url, merged);
        __nativeLog('[AXIOS_INSTANCE] get: ' + (finalUrl ? finalUrl.substring(0, 100) : ''));
        return globalThis.fetch(finalUrl, merged).then(function(resp) {
            return globalThis.__toAxiosResponse(resp, finalUrl);
        }).catch(function(e) {
            var err = e;
            if (!err.response) { err.response = { data: null, status: 0, statusText: e.message || 'Network Error', headers: {} }; }
            throw err;
        });
    };
    instance.post = function(url, data, config) {
        config = config || {};
        var merged = { method: 'POST', body: typeof data === 'object' ? JSON.stringify(data) : String(data || ''), baseURL: baseConfig.baseURL, params: config.params || baseConfig.params, headers: config.headers || baseConfig.headers };
        var finalUrl = globalThis.__buildAxiosUrl(url, merged);
        __nativeLog('[AXIOS_INSTANCE] post: ' + (finalUrl ? finalUrl.substring(0, 100) : ''));
        return globalThis.fetch(finalUrl, merged).then(function(resp) {
            return globalThis.__toAxiosResponse(resp, finalUrl);
        }).catch(function(e) {
            var err = e;
            if (!err.response) { err.response = { data: null, status: 0, statusText: e.message || 'Network Error', headers: {} }; }
            throw err;
        });
    };
    instance.put = function(url, data, config) {
        config = config || {};
        var merged = { method: 'PUT', body: typeof data === 'object' ? JSON.stringify(data) : String(data || ''), baseURL: baseConfig.baseURL, params: config.params || baseConfig.params, headers: config.headers || baseConfig.headers };
        var finalUrl = globalThis.__buildAxiosUrl(url, merged);
        return globalThis.fetch(finalUrl, merged).then(function(resp) {
            return globalThis.__toAxiosResponse(resp, finalUrl);
        }).catch(function(e) {
            var err = e;
            if (!err.response) { err.response = { data: null, status: 0, statusText: e.message || 'Network Error', headers: {} }; }
            throw err;
        });
    };
    instance.delete = function(url, config) {
        config = config || {};
        var merged = { method: 'DELETE', baseURL: baseConfig.baseURL, params: config.params || baseConfig.params, headers: config.headers || baseConfig.headers };
        var finalUrl = globalThis.__buildAxiosUrl(url, merged);
        return globalThis.fetch(finalUrl, merged).then(function(resp) {
            return globalThis.__toAxiosResponse(resp, finalUrl);
        }).catch(function(e) {
            var err = e;
            if (!err.response) { err.response = { data: null, status: 0, statusText: e.message || 'Network Error', headers: {} }; }
            throw err;
        });
    };
    instance.defaults = { headers: { common: baseConfig.headers || {} } };
    instance.interceptors = {
        request: { use: function() {} },
        response: { use: function() {} }
    };
    instance.create = function(cfg) { return globalThis.__lxPackages['axios'].create(cfg); };
    return instance;
};
globalThis.__lxPackages['axios'].defaults = { headers: { common: {} } };
globalThis.__lxPackages['axios'].interceptors = {
    request: { use: function() {} },
    response: { use: function() {} }
};
globalThis.__lxPackages['axios'].CancelToken = function(executor) {
    var cancel;
    this.promise = new Promise(function(resolve) { cancel = resolve; });
    this.cancel = cancel;
    if (typeof executor === 'function') executor(cancel);
};
globalThis.__lxPackages['axios'].isCancel = function() { return false; };
globalThis.__lxPackages['axios'].all = function(promises) { return Promise.all(promises); };
globalThis.__lxPackages['axios'].spread = function(callback) { return function(arr) { return callback.apply(null, arr); }; };
globalThis.__lxPackages['axios'].Axios = function() {};
Object.defineProperty(globalThis.__lxPackages['axios'], 'default', { get: function() { return globalThis.__lxPackages['axios']; } });

globalThis.__lxPackages['dayjs'] = function(d) {
    var date;
    if (d === undefined || d === null) {
        date = new Date();
    } else if (typeof d === 'number') {
        date = new Date(d);
    } else if (d instanceof Date) {
        date = d;
    } else if (typeof d === 'string') {
        date = new Date(d.replace(/-/g, '/'));
    } else {
        date = new Date(d);
    }
    var pad = function(n) { return n < 10 ? '0' + n : '' + n; };
    var instance = {
        format: function(fmt) {
            if (!fmt) return date.toISOString();
            var y = date.getFullYear();
            var M = date.getMonth() + 1;
            var d2 = date.getDate();
            var H = date.getHours();
            var m = date.getMinutes();
            var s = date.getSeconds();
            var ms = date.getMilliseconds();
            return fmt
                .replace(/YYYY/g, y)
                .replace(/YY/g, String(y).slice(-2))
                .replace(/MM/g, pad(M))
                .replace(/M/g, M)
                .replace(/DD/g, pad(d2))
                .replace(/D/g, d2)
                .replace(/HH/g, pad(H))
                .replace(/H/g, H)
                .replace(/hh/g, pad(H > 12 ? H - 12 : H))
                .replace(/h/g, H > 12 ? H - 12 : H)
                .replace(/mm/g, pad(m))
                .replace(/m/g, m)
                .replace(/ss/g, pad(s))
                .replace(/s/g, s)
                .replace(/SSS/g, pad(ms).padStart(3, '0'))
                .replace(/A/g, H >= 12 ? 'PM' : 'AM')
                .replace(/a/g, H >= 12 ? 'pm' : 'am');
        },
        unix: function() { return Math.floor(date.getTime() / 1000); },
        valueOf: function() { return date.getTime(); },
        toDate: function() { return new Date(date.getTime()); },
        isValid: function() { return !isNaN(date.getTime()); },
        year: function() { return date.getFullYear(); },
        month: function() { return date.getMonth(); },
        day: function() { return date.getDay(); },
        hour: function() { return date.getHours(); },
        minute: function() { return date.getMinutes(); },
        second: function() { return date.getSeconds(); },
        millisecond: function() { return date.getMilliseconds(); },
        set: function(unit, val) {
            var d3 = new Date(date.getTime());
            if (unit === 'year' || unit === 'years') d3.setFullYear(val);
            else if (unit === 'month' || unit === 'months') d3.setMonth(val);
            else if (unit === 'date' || unit === 'day' || unit === 'days') d3.setDate(val);
            else if (unit === 'hour' || unit === 'hours') d3.setHours(val);
            else if (unit === 'minute' || unit === 'minutes') d3.setMinutes(val);
            else if (unit === 'second' || unit === 'seconds') d3.setSeconds(val);
            else if (unit === 'millisecond' || unit === 'milliseconds') d3.setMilliseconds(val);
            return globalThis.__lxPackages['dayjs'](d3);
        },
        add: function(val, unit) {
            var d3 = new Date(date.getTime());
            if (unit === 'year' || unit === 'years') d3.setFullYear(d3.getFullYear() + val);
            else if (unit === 'month' || unit === 'months') d3.setMonth(d3.getMonth() + val);
            else if (unit === 'day' || unit === 'days' || unit === 'date') d3.setDate(d3.getDate() + val);
            else if (unit === 'hour' || unit === 'hours') d3.setHours(d3.getHours() + val);
            else if (unit === 'minute' || unit === 'minutes') d3.setMinutes(d3.getMinutes() + val);
            else if (unit === 'second' || unit === 'seconds') d3.setSeconds(d3.getSeconds() + val);
            else if (unit === 'millisecond' || unit === 'milliseconds') d3.setTime(d3.getTime() + val);
            return globalThis.__lxPackages['dayjs'](d3);
        },
        subtract: function(val, unit) { return instance.add(-val, unit); },
        startOf: function(unit) {
            var d3 = new Date(date.getTime());
            if (unit === 'year') { d3.setMonth(0); d3.setDate(1); d3.setHours(0,0,0,0); }
            else if (unit === 'month') { d3.setDate(1); d3.setHours(0,0,0,0); }
            else if (unit === 'day' || unit === 'date') { d3.setHours(0,0,0,0); }
            else if (unit === 'hour') { d3.setMinutes(0,0,0); }
            else if (unit === 'minute') { d3.setSeconds(0,0); }
            else if (unit === 'second') { d3.setMilliseconds(0); }
            return globalThis.__lxPackages['dayjs'](d3);
        },
        endOf: function(unit) {
            var d3 = new Date(date.getTime());
            if (unit === 'year') { d3.setMonth(11); d3.setDate(31); d3.setHours(23,59,59,999); }
            else if (unit === 'month') { d3.setMonth(d3.getMonth()+1); d3.setDate(0); d3.setHours(23,59,59,999); }
            else if (unit === 'day' || unit === 'date') { d3.setHours(23,59,59,999); }
            else if (unit === 'hour') { d3.setMinutes(59,59,999); }
            else if (unit === 'minute') { d3.setSeconds(59,999); }
            else if (unit === 'second') { d3.setMilliseconds(999); }
            return globalThis.__lxPackages['dayjs'](d3);
        },
        diff: function(other, unit) {
            var d3 = other instanceof Date ? other : (other && other.toDate ? other.toDate() : new Date(other));
            var diff = date.getTime() - d3.getTime();
            if (unit === 'year' || unit === 'years') return date.getFullYear() - d3.getFullYear();
            if (unit === 'month' || unit === 'months') return (date.getFullYear() - d3.getFullYear()) * 12 + date.getMonth() - d3.getMonth();
            if (unit === 'day' || unit === 'days' || unit === 'date') return Math.floor(diff / 86400000);
            if (unit === 'hour' || unit === 'hours') return Math.floor(diff / 3600000);
            if (unit === 'minute' || unit === 'minutes') return Math.floor(diff / 60000);
            if (unit === 'second' || unit === 'seconds') return Math.floor(diff / 1000);
            if (unit === 'millisecond' || unit === 'milliseconds') return diff;
            return diff;
        }
    };
    return instance;
};
globalThis.__lxPackages['dayjs'].unix = function(timestamp) {
    return globalThis.__lxPackages['dayjs'](timestamp * 1000);
};
globalThis.__lxPackages['dayjs'].extend = function() {};
Object.defineProperty(globalThis.__lxPackages['dayjs'], 'default', { get: function() { return globalThis.__lxPackages['dayjs']; } });

globalThis.__lxPackages['qs'] = {
    parse: function(str) {
        var obj = {};
        String(str).replace(/^\?/, '').split('&').forEach(function(pair) {
            var idx = pair.indexOf('=');
            if (idx > 0) obj[decodeURIComponent(pair.substring(0, idx))] = decodeURIComponent(pair.substring(idx + 1));
        });
        return obj;
    },
    stringify: function(obj) {
        return Object.keys(obj || {}).map(function(k) { return encodeURIComponent(k) + '=' + encodeURIComponent(obj[k]); }).join('&');
    }
};
Object.defineProperty(globalThis.__lxPackages['qs'], 'default', { get: function() { return globalThis.__lxPackages['qs']; } });

globalThis.__lxPackages['big-integer'] = function(v) {
    return { value: v, toString: function() { return String(v); }, add: function(n) { return globalThis.__lxPackages['big-integer'](parseInt(v||0)+parseInt(n||0)); } };
};
Object.defineProperty(globalThis.__lxPackages['big-integer'], 'default', { get: function() { return globalThis.__lxPackages['big-integer']; } });

globalThis.__lxPackages['pako'] = {
    inflate: function() { throw new Error('pako not implemented'); },
    deflate: function() { throw new Error('pako not implemented'); }
};
Object.defineProperty(globalThis.__lxPackages['pako'], 'default', { get: function() { return globalThis.__lxPackages['pako']; } });

globalThis.__lxPackages['buffer'] = {
    Buffer: {
        from: function(data, enc) {
            if (data instanceof Uint8Array) return data;
            if (typeof data === 'string') return new TextEncoder().encode(data);
            return new Uint8Array(0);
        },
        isBuffer: function(b) { return b instanceof Uint8Array; },
        alloc: function(n) { return new Uint8Array(n); },
        concat: function(list) {
            var total = 0;
            list.forEach(function(b) { total += b.length; });
            var result = new Uint8Array(total);
            var offset = 0;
            list.forEach(function(b) { result.set(b, offset); offset += b.length; });
            return result;
        }
    }
};
Object.defineProperty(globalThis.__lxPackages['buffer'], 'default', { get: function() { return globalThis.__lxPackages['buffer']; } });

globalThis.__lxPackages['webdav'] = {
    createClient: function() { throw new Error('webdav not implemented'); }
};
Object.defineProperty(globalThis.__lxPackages['webdav'], 'default', { get: function() { return globalThis.__lxPackages['webdav']; } });

globalThis.require = function(moduleName) {
    if (globalThis.__lxPackages[moduleName]) {
        return globalThis.__lxPackages[moduleName];
    }
    __nativeLog('[REQUIRE] NOT FOUND: ' + moduleName + ', returning stub');
    var stub = {};
    stub.default = stub;
    return stub;
};

globalThis.Buffer = globalThis.__lxPackages['buffer'].Buffer;

globalThis.atob = function(s) { try { return atob(s); } catch(e) { return ''; } };
globalThis.btoa = function(s) { try { return btoa(s); } catch(e) { return ''; } };

globalThis.__extractPluginInfo = function() {
    var e = null;
    if (typeof __mfModule !== 'undefined' && __mfModule && __mfModule.exports) {
        e = __mfModule.exports;
    }
    if (!e && typeof __pluginInstance !== 'undefined') {
        e = __pluginInstance;
    }
    if (!e) {
        e = (typeof module !== 'undefined' && module && module.exports) ? module.exports : null;
    }
    if (!e) return '{}';
    __nativeLog('[MF_INFO] exports type=' + typeof e + ' keys=' + JSON.stringify(Object.keys(e).slice(0, 20)));
    if (e.default && typeof e.default === 'object') {
        e = e.default;
        __nativeLog('[MF_INFO] using exports.default, keys=' + JSON.stringify(Object.keys(e).slice(0, 20)));
    }
    var obj = {};
    var fields = ['name', 'version', 'author', 'description', 'homepage', 'pluginName', 'title', 'platform'];
    for (var i = 0; i < fields.length; i++) {
        try {
            if (e[fields[i]] !== undefined) {
                obj[fields[i]] = String(e[fields[i]]);
            }
        } catch (x) {}
    }
    if (Object.keys(obj).length === 0) {
        var ek = Object.keys(e);
        for (var i = 0; i < ek.length; i++) {
            try {
                var v = e[ek[i]];
                if (v && typeof v === 'object' && v.name) {
                    obj.name = v.name;
                    if (v.version) obj.version = v.version;
                    if (v.author) obj.author = v.author;
                    break;
                }
            } catch (x) {}
        }
    }
    return JSON.stringify(obj);
};

globalThis.__extractPluginSources = function() {
    var e = null;
    if (typeof __mfModule !== 'undefined' && __mfModule && __mfModule.exports) {
        e = __mfModule.exports;
    }
    if (!e && typeof __pluginInstance !== 'undefined') {
        e = __pluginInstance;
    }
    if (!e) {
        e = (typeof module !== 'undefined' && module && module.exports) ? module.exports : null;
    }
    if (!e) return '[]';
    if (e.default && typeof e.default === 'object') e = e.default;
    if (e.sources && Array.isArray(e.sources)) return JSON.stringify(e.sources);
    if (e.sourceList && Array.isArray(e.sourceList)) return JSON.stringify(e.sourceList);
    if (e.platform && typeof e.platform === 'string' && e.platform.length > 0) {
        var name = e.platform.toLowerCase();
        var key;
        if (name.indexOf('酷我') !== -1 || name.indexOf('kuwo') !== -1 || name.indexOf('kw') !== -1) key = 'kw';
        else if (name.indexOf('酷狗') !== -1 || name.indexOf('kugou') !== -1 || name.indexOf('kg') !== -1) key = 'kg';
        else if (name.indexOf('网易云') !== -1 || name.indexOf('netease') !== -1 || name.indexOf('wy') !== -1) key = 'wy';
        else if (name.indexOf('qq') !== -1 || name.indexOf('QQ音乐') !== -1 || name.indexOf('tx') !== -1) key = 'tx';
        else if (name.indexOf('咪咕') !== -1 || name.indexOf('migu') !== -1 || name.indexOf('mg') !== -1) key = 'mg';
        else if (name.indexOf('抖音') !== -1 || name.indexOf('douyin') !== -1 || name.indexOf('dy') !== -1) key = 'dy';
        else key = e.platform;
        return JSON.stringify([key]);
    }
    var keys = [];
    if (typeof e.search === 'function') keys.push('search');
    if (typeof e.getMusicUrl === 'function' || typeof e.getMusicUrlByName === 'function') keys.push('play');
    if (typeof e.getLyric === 'function' || typeof e.getLyricByName === 'function') keys.push('lyric');
    if (typeof e.getTopLists === 'function') keys.push('topLists');
    if (typeof e.getPlaylistDetail === 'function') keys.push('playlist');
    if (typeof e.getAlbumInfo === 'function') keys.push('album');
    return JSON.stringify(keys);
};
