// drpy.js — runtime helpers for drpy v1 function-style rules (and the drpy2 engine).
// Loaded lazily (dynamic import) only when a drpy2 / v1 / cat rule is detected, so
// normal Spider-class sources pay nothing. Sets the globals those rules expect:
//   req(url, obj)      — synchronous request, returns {content, headers, code}
//                        (the drpy2 engine reads res.content synchronously).
//   request(url, obj)  — async request, resolves to the body string.
//   pdfa/pdfh/pd       — cheerio HTML helpers (drpy `&&` selector syntax).
//   _pdfh/_pdfa        — extract from a cheerio element returned by pdfa/pd.
//   setResult(result)  — stash a result for rules that use it instead of returning.
//   log(msg)           — console log.
// `fetch` and `setTimeout` are NOT defined here: they come from the QuickJS
// WebPlatform if the runtime provides them (verify at build time).
import host from 'catvod:host';
import {cheerio} from './drpy2/drpy-core-lite.min.js';

let dollar = null; // cheerio `$` from the most recent load; used to wrap results.

function loadHtml(html) {
    if (typeof html === 'string') {
        dollar = cheerio.load(html);
        return dollar;
    }
    return dollar;
}

function applySelector($, root, parse) {
    const parts = String(parse || '').split('&&').map(s => s.trim()).filter(Boolean);
    if (!parts.length) return null;
    let sel = root ? root.find(parts[0]) : $(parts[0]);
    for (let i = 1; i < parts.length; i++) sel = sel.find(parts[i]);
    return sel;
}

// pdfa(html, parse) -> array of cheerio elements matching `parse` (drpy `&&` syntax).
// `html` is an HTML string, or a cheerio element from an earlier pdfa/pd call.
export function pdfa(html, parse) {
    const $ = loadHtml(html);
    if (!$) return [];
    let sel;
    if (typeof html === 'string') {
        sel = applySelector($, null, parse);
    } else if (html && typeof html.find === 'function') {
        sel = applySelector($, html, parse);
    } else {
        return [];
    }
    if (!sel) return [];
    const out = [];
    sel.each((_, el) => out.push($(el)));
    return out;
}

// pd(html, parse, base_url) -> first matching cheerio element, or null.
export function pd(html, parse, baseUrl) {
    const items = pdfa(html, parse);
    return items.length ? items[0] : null;
}

// pdfh(html, parse) -> inner HTML of the first match, or ''.
export function pdfh(html, parse) {
    const el = pd(html, parse);
    if (!el) return '';
    try { return el.html() || ''; } catch (e) { return ''; }
}

// _pdfh(el, type, def): extract from a cheerio element.
//   type 'Text' -> text, 'Html' -> html, otherwise the attribute named `type`.
// Returns `def` when the value is empty/undefined.
export function _pdfh(el, type, def) {
    if (!el) return def === undefined ? '' : def;
    let value;
    try {
        if (type === 'Text') value = el.text();
        else if (type === 'Html') value = el.html();
        else value = el.attr(type);
    } catch (e) {
        value = undefined;
    }
    if (value === undefined || value === null || value === '') return def === undefined ? '' : def;
    return value;
}

// _pdfa(el, parse) -> pdfa within a cheerio element.
export function _pdfa(el, parse) {
    if (!el || typeof el.find !== 'function') return [];
    return pdfa(el, parse);
}

let storedResult = undefined;

export function setResult(result) {
    storedResult = result;
    return result;
}

export function getStoredResult() {
    return storedResult;
}

export function log(message) {
    try { console.log(message); } catch (e) { /* console unavailable */ }
}

// Synchronous request. host.reqSync is bound in Global.java (Network.reqSync):
// it blocks on the same OkHttp call the async req uses and returns the same
// {content, headers, code} object, so the drpy2 engine can read res.content.
export function req(url, obj) {
    if (typeof host.reqSync !== 'function') throw new Error('drpy needs a synchronous req, but host.reqSync is missing');
    return host.reqSync(url, obj || {});
}

// Async request. Resolves to the body string (what drpy v1 rules expect:
// `let html = await request(url)` then `pdfa(html, ...)`).
export async function request(url, obj) {
    const res = await host.req(url, obj || {});
    if (typeof res === 'string') {
        try { return JSON.parse(res).content ?? res; } catch (e) { return res; }
    }
    if (res && typeof res === 'object') return res.content ?? '';
    return '';
}

export function installDrpyGlobals() {
    if (typeof globalThis.req !== 'function') globalThis.req = req;
    if (typeof globalThis.request !== 'function') globalThis.request = request;
    if (typeof globalThis.pdfa !== 'function') globalThis.pdfa = pdfa;
    if (typeof globalThis.pd !== 'function') globalThis.pd = pd;
    if (typeof globalThis.pdfh !== 'function') globalThis.pdfh = pdfh;
    if (typeof globalThis._pdfh !== 'function') globalThis._pdfh = _pdfh;
    if (typeof globalThis._pdfa !== 'function') globalThis._pdfa = _pdfa;
    if (typeof globalThis.setResult !== 'function') globalThis.setResult = setResult;
    if (typeof globalThis.log !== 'function') globalThis.log = log;
    if (typeof globalThis.joinUrl !== 'function') globalThis.joinUrl = host.joinUrl;
}
