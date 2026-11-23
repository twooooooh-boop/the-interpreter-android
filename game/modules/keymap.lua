-- modules/keymap.lua
-- Editable letter <-> key tables for every cipher language.
--
--   * every plain letter (a-z, A-Z) can own several "keys" (cipher symbols)
--   * the FIRST key of a letter is the "primary" one and is used when encoding
--   * all keys of a letter are understood when decoding
--   * edits are stored per language in keymap.dat (love save folder);
--     a language that was never edited just uses the built-in table.

local utf8 = require("utf8")

local Keymap = {}

Keymap.MAX_KEYS = 8        -- keys per letter
Keymap.MAX_KEY_CHARS = 6   -- characters per key

local FILE   = "keymap.dat"
local HEADER = "ELDAKEYS1"

Keymap.LOWER, Keymap.UPPER = {}, {}
for c = 97, 122 do
    Keymap.LOWER[#Keymap.LOWER + 1] = string.char(c)
    Keymap.UPPER[#Keymap.UPPER + 1] = string.char(c - 32)
end

local defaults = {}   -- defaults[lang][letter] = { key, key, ... }
local custom   = {}   -- custom[lang][letter]   = { key, key, ... }  (only edited languages)
local version  = 1

----------------------------------------------------------------------
-- helpers
----------------------------------------------------------------------

local function isLetter(s)
    return type(s) == "string" and #s == 1 and s:match("%a") ~= nil
end

local function copyMap(src)
    local out = {}
    for letter, list in pairs(src) do
        local l = {}
        for i, k in ipairs(list) do l[i] = k end
        out[letter] = l
    end
    return out
end

local function fillLetters(map)
    for _, set in ipairs({ Keymap.LOWER, Keymap.UPPER }) do
        for _, letter in ipairs(set) do
            map[letter] = map[letter] or {}
        end
    end
    return map
end

-- built-in tables are written as  symbol -> letter ; turn them into
-- letter -> { symbols } with a stable order (keys that contain a lowercase
-- ascii letter first - same preference the old encoder had - then bytewise).
local function invert(map)
    local out = {}
    for sym, letter in pairs(map) do
        out[letter] = out[letter] or {}
        table.insert(out[letter], sym)
    end
    for _, list in pairs(out) do
        table.sort(list, function(a, b)
            local al, bl = a:find("%l") ~= nil, b:find("%l") ~= nil
            if al ~= bl then return al end
            return a < b
        end)
    end
    return out
end

local function toHex(s)
    return (s:gsub(".", function(c) return string.format("%02x", c:byte()) end))
end

local function fromHex(h)
    if #h % 2 ~= 0 or h:find("%X") then return nil end
    return (h:gsub("%x%x", function(x) return string.char(tonumber(x, 16)) end))
end

local function removeFromList(list, key)
    for i = #list, 1, -1 do
        if list[i] == key then table.remove(list, i) end
    end
end

local function ensureCustom(lang)
    if not custom[lang] then
        custom[lang] = fillLetters(copyMap(defaults[lang] or {}))
    end
    return custom[lang]
end

local function touch()
    version = version + 1
    Keymap.save()
end

----------------------------------------------------------------------
-- public api
----------------------------------------------------------------------

function Keymap.version() return version end

function Keymap.isCustom(lang) return custom[lang] ~= nil end

function Keymap.validateKey(key)
    if type(key) ~= "string" or key == "" then return false, "Type a key first" end
    if key:find("[%s%c]") then return false, "No spaces allowed" end
    local n = utf8.len(key)
    if not n then return false, "Invalid text" end
    if n > Keymap.MAX_KEY_CHARS then
        return false, "Max " .. Keymap.MAX_KEY_CHARS .. " characters"
    end
    return true
end

local EMPTY = {}

-- list of keys for a letter (do not modify the returned table)
function Keymap.keysFor(lang, letter)
    local src = custom[lang] or defaults[lang]
    return (src and src[letter]) or EMPTY
end

-- which letter currently owns `key` in this language (nil if nobody)
function Keymap.owner(lang, key)
    local src = custom[lang] or defaults[lang]
    if not src then return nil end
    for letter, list in pairs(src) do
        for _, k in ipairs(list) do
            if k == key then return letter end
        end
    end
    return nil
end

-- returns ok, message
function Keymap.addKey(lang, letter, key)
    if not isLetter(letter) then return false, "Bad letter" end
    local ok, err = Keymap.validateKey(key)
    if not ok then return false, err end

    local owner = Keymap.owner(lang, key)
    if owner == letter then
        return false, "Already a key for '" .. letter .. "'"
    end
    if #Keymap.keysFor(lang, letter) >= Keymap.MAX_KEYS then
        return false, "Max " .. Keymap.MAX_KEYS .. " keys per letter"
    end

    local map = ensureCustom(lang)
    local note = "Added"
    if owner then
        removeFromList(map[owner], key)
        note = "Moved here from '" .. owner .. "'"
    end
    table.insert(map[letter], key)
    touch()
    return true, note
end

function Keymap.removeKey(lang, letter, index)
    local list = Keymap.keysFor(lang, letter)
    if not list[index] then return false end
    local map = ensureCustom(lang)
    table.remove(map[letter], index)
    touch()
    return true
end

-- move key #index to the front (the front key is used for encoding)
function Keymap.makePrimary(lang, letter, index)
    local list = Keymap.keysFor(lang, letter)
    if index < 2 or not list[index] then return false end
    local map = ensureCustom(lang)
    local k = table.remove(map[letter], index)
    table.insert(map[letter], 1, k)
    touch()
    return true
end

function Keymap.clearLetter(lang, letter)
    if #Keymap.keysFor(lang, letter) == 0 then return false end
    local map = ensureCustom(lang)
    map[letter] = {}
    touch()
    return true
end

function Keymap.resetLanguage(lang)
    if not custom[lang] then return false end
    custom[lang] = nil
    touch()
    return true
end

-- Build the lookup tables used by the interpreter.
--   decodeMap[key]    = letter
--   encodeMap[letter] = primary key
--   lens              = distinct key byte-lengths, longest first (for decoding)
function Keymap.build(lang)
    local src = custom[lang] or defaults[lang] or {}
    local decodeMap, encodeMap, seen, lens = {}, {}, {}, {}
    for letter, list in pairs(src) do
        for i, key in ipairs(list) do
            decodeMap[key] = letter
            if i == 1 then encodeMap[letter] = key end
            if not seen[#key] then
                seen[#key] = true
                lens[#lens + 1] = #key
            end
        end
    end
    table.sort(lens, function(a, b) return a > b end)
    return decodeMap, encodeMap, lens
end

----------------------------------------------------------------------
-- persistence
----------------------------------------------------------------------

function Keymap.save()
    local langs = {}
    for lang in pairs(custom) do langs[#langs + 1] = lang end
    if #langs == 0 then
        pcall(love.filesystem.remove, FILE)
        return true
    end
    table.sort(langs)

    local out = { HEADER }
    for _, lang in ipairs(langs) do
        for _, set in ipairs({ Keymap.LOWER, Keymap.UPPER }) do
            for _, letter in ipairs(set) do
                local hex = {}
                for i, key in ipairs(custom[lang][letter] or EMPTY) do
                    hex[i] = toHex(key)
                end
                out[#out + 1] = lang .. "\t" .. letter .. "\t" .. table.concat(hex, ",")
            end
        end
    end
    local ok = pcall(love.filesystem.write, FILE, table.concat(out, "\n") .. "\n")
    return ok
end

function Keymap.load()
    local ok, data = pcall(love.filesystem.read, FILE)
    if not ok or type(data) ~= "string" then return end

    local lines = {}
    for line in data:gmatch("[^\r\n]+") do lines[#lines + 1] = line end
    if lines[1] ~= HEADER then return end

    local loaded, used = {}, {}
    for i = 2, #lines do
        local lang, letter, hexlist = lines[i]:match("^(%w+)\t(%a)\t?(.*)$")
        if lang and defaults[lang] then
            loaded[lang] = loaded[lang] or {}
            used[lang] = used[lang] or {}
            local list = {}
            for h in hexlist:gmatch("[^,]+") do
                local key = fromHex(h)
                if key and Keymap.validateKey(key) and not used[lang][key] then
                    used[lang][key] = true
                    list[#list + 1] = key
                end
            end
            loaded[lang][letter] = list
        end
    end
    for lang, map in pairs(loaded) do custom[lang] = fillLetters(map) end
end

-- builtin = { demon = { [symbol] = letter, ... }, ridmik = {...}, ... }
function Keymap.init(builtin)
    defaults = {}
    for lang, map in pairs(builtin) do defaults[lang] = invert(map) end
    custom = {}
    Keymap.load()
    version = version + 1
end

return Keymap
