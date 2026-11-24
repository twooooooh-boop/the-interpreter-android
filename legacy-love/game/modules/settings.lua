-- modules/settings.lua
-- "SETTINGS" tab: choose which key(s) stand for each letter, per language.
--
--   * tap a language chip      -> pick the cipher language to edit
--   * a-z / A-Z chips          -> switch between lower and upper case letters
--   * tap a letter row         -> open the key editor for that letter
--   * editor: type a key with the normal phone keyboard, press ADD
--             PRIMARY = key used when encoding,  DEL = remove key
--   * RESET LANG (tap twice)   -> back to the built-in keys of that language
--
-- The module draws everything itself and keeps its own touch handling
-- (tap vs. drag-to-scroll), so main.lua only has to forward events.

local utf8   = require("utf8")
local Keymap = require("modules.keymap")

local Settings = {}

local env = nil   -- filled by Settings.init

local S = {
    caseSet    = "lower",   -- "lower" | "upper"
    scroll     = 0,         -- list scroll offset (<= 0)
    maxScroll  = 0,
    editor     = nil,       -- { letter = "a", buffer = "", blink = 0 }
    press      = nil,       -- current finger/mouse press
    rects      = {},        -- tap areas, rebuilt every frame (later = on top)
    msg        = nil,
    msgKind    = "info",
    msgTimer   = 0,
    resetArmed = 0,
    fonts      = nil,
    fontU      = nil,
    u          = 1,
}

local C = {
    bg      = { 0.07, 0.07, 0.09 },
    panel   = { 0.11, 0.11, 0.14 },
    row     = { 0.14, 0.14, 0.18 },
    chip    = { 0.14, 0.14, 0.18 },
    accent  = { 0.2, 0.4, 0.8 },
    green   = { 0.4, 1.0, 0.4 },
    red     = { 0.72, 0.08, 0.08 },
    text    = { 0.92, 0.92, 0.92 },
    dim     = { 0.55, 0.55, 0.6 },
    err     = { 1.0, 0.45, 0.45 },
}

----------------------------------------------------------------------
-- small helpers
----------------------------------------------------------------------

local function langKey()
    return env.languages[env.getLang()]:lower()
end

local function say(text, kind)
    S.msg, S.msgKind, S.msgTimer = text, kind or "info", 3.5
end

local function ensureFonts(u)
    if S.fonts and S.fontU == u then return end
    S.fontU = u
    local noto, one = "fonts/NotoSans-Regular.ttf", "fonts/One Slice.otf"
    S.fonts = {
        small = love.graphics.newFont(noto, math.max(10, math.floor(12 * u + 0.5))),
        body  = love.graphics.newFont(noto, math.floor(15 * u + 0.5)),
        big   = love.graphics.newFont(noto, math.floor(22 * u + 0.5)),
        title = love.graphics.newFont(one, math.floor(15 * u + 0.5)),
    }
end

local function addRect(x, y, w, h, onTap)
    S.rects[#S.rects + 1] = { x = x, y = y, w = w, h = h, onTap = onTap }
end

local function inside(r, x, y)
    return x >= r.x and x <= r.x + r.w and y >= r.y and y <= r.y + r.h
end

local function clampScroll(v)
    return math.max(-S.maxScroll, math.min(0, v))
end

local function trimLastChar(s)
    if s == "" then return s end
    local off = utf8.offset(s, -1)
    return off and s:sub(1, off - 1) or ""
end

local function limitChars(s)
    while (utf8.len(s) or 0) > Keymap.MAX_KEY_CHARS do s = trimLastChar(s) end
    return s
end

----------------------------------------------------------------------
-- editor open / close / actions
----------------------------------------------------------------------

local function openEditor(letter)
    S.editor = { letter = letter, buffer = "", blink = 0 }
    S.msg = nil
    love.keyboard.setTextInput(true)
end

local function closeEditor()
    if S.editor and env.isAndroid() then love.keyboard.setTextInput(false) end
    S.editor = nil
end

local function doAdd()
    local ed = S.editor
    if not ed then return end
    local ok, msg = Keymap.addKey(langKey(), ed.letter, ed.buffer)
    if ok then ed.buffer = "" end
    say(msg, ok and "ok" or "err")
end

----------------------------------------------------------------------
-- public: lifecycle
----------------------------------------------------------------------

function Settings.init(e)
    env = e
end

function Settings.enter()
    S.scroll, S.press, S.resetArmed = 0, nil, 0
    S.editor = nil
    if env.deactivateKeyboard then env.deactivateKeyboard() end
    if env.isAndroid() then love.keyboard.setTextInput(false) end
end

function Settings.leave()
    closeEditor()
    S.press, S.resetArmed = nil, 0
end

function Settings.update(dt)
    if S.msgTimer > 0 then S.msgTimer = S.msgTimer - dt end
    if S.resetArmed > 0 then S.resetArmed = S.resetArmed - dt end
    if S.editor then S.editor.blink = (S.editor.blink + dt) % 1 end
end

----------------------------------------------------------------------
-- public: input
----------------------------------------------------------------------

function Settings.pressed(x, y)
    if y >= love.graphics.getHeight() - env.tabH then return false end  -- tab bar: not ours
    S.press = { x = x, y = y, scroll = S.scroll, dragging = false }
    return true
end

function Settings.moved(x, y)
    local p = S.press
    if not p or S.editor then return end
    if not p.dragging and math.abs(y - p.y) > 10 * S.u then p.dragging = true end
    if p.dragging then S.scroll = clampScroll(p.scroll + (y - p.y)) end
end

function Settings.released(x, y)
    local p = S.press
    S.press = nil
    if not p or p.dragging then return end
    for i = #S.rects, 1, -1 do
        local r = S.rects[i]
        if inside(r, p.x, p.y) then
            if r.onTap then r.onTap() end
            return
        end
    end
end

function Settings.wheel(dy)
    if S.editor then return end
    S.scroll = clampScroll(S.scroll + dy * 40)
end

function Settings.textinput(t)
    local ed = S.editor
    if not ed then return end
    t = t:gsub("[%s%c]", "")          -- keys never contain spaces / control chars
    if t == "" then return end
    ed.buffer = limitChars(ed.buffer .. t)
    ed.blink = 0
end

-- returns true when the key was used up (so main.lua must not quit on "escape")
function Settings.keypressed(key)
    local ed = S.editor
    if not ed then return false end
    if key == "backspace" then
        ed.buffer = trimLastChar(ed.buffer)
        ed.blink = 0
        return true
    elseif key == "return" or key == "kpenter" then
        doAdd()
        return true
    elseif key == "escape" then
        closeEditor()
        return true
    end
    return false
end

----------------------------------------------------------------------
-- drawing helpers
----------------------------------------------------------------------

local function fillRect(x, y, w, h, color, radius, alpha)
    love.graphics.setColor(color[1], color[2], color[3], alpha or 1)
    love.graphics.rectangle("fill", x, y, w, h, radius or 6, radius or 6)
end

local function lighten(color, d)
    return { math.min(1, color[1] + d), math.min(1, color[2] + d), math.min(1, color[3] + d) }
end

-- draws a tappable button; text is centred
local function button(text, x, y, w, h, bg, fontKey, onTap, mx, my, fg)
    local hover = mx >= x and mx <= x + w and my >= y and my <= y + h
    fillRect(x, y, w, h, hover and lighten(bg, 0.08) or bg, 6 * S.u)
    local f = S.fonts[fontKey]
    love.graphics.setFont(f)
    love.graphics.setColor(fg or C.text)
    love.graphics.printf(text, x, y + (h - f:getHeight()) / 2, w, "center")
    if onTap then addRect(x, y, w, h, onTap) end
end

----------------------------------------------------------------------
-- editor overlay
----------------------------------------------------------------------

local function drawEditor(sw, viewBottom, mx, my)
    local ed, F, u = S.editor, S.fonts, S.u
    local lang = langKey()
    local keys = Keymap.keysFor(lang, ed.letter)
    local n = #keys
    local rows = math.max(n, 1)

    -- dim everything behind the panel; tapping it closes the editor
    fillRect(0, 0, sw, viewBottom, { 0, 0, 0 }, 0, 0.7)
    addRect(0, 0, sw, viewBottom, closeEditor)

    local m = math.floor(12 * u)
    local pad = math.floor(10 * u)
    local px, py, pw = m, m, sw - m * 2

    local titleH = F.title:getHeight()
    local fieldH = math.floor(38 * u)
    local hintH  = F.small:getHeight() * 2
    local rowH   = math.floor(32 * u)
    local pitch  = rowH + math.floor(4 * u)
    local btnH   = math.floor(36 * u)

    local yTitle = py + pad
    local yField = yTitle + titleH + pad
    local yHint  = yField + fieldH + math.floor(6 * u)
    local yRows  = yHint + hintH + math.floor(6 * u)
    local yBtns  = yRows + rows * pitch + math.floor(4 * u)
    local panelH = yBtns + btnH + pad - py

    fillRect(px, py, pw, panelH, C.panel, 10 * u)
    love.graphics.setColor(C.accent)
    love.graphics.rectangle("line", px, py, pw, panelH, 10 * u, 10 * u)
    addRect(px, py, pw, panelH, nil)   -- swallow taps on the panel

    -- title + big letter
    love.graphics.setFont(F.title)
    love.graphics.setColor(C.text)
    love.graphics.print("EDIT KEYS - " .. lang:upper(), px + pad, yTitle)
    love.graphics.setFont(F.big)
    love.graphics.setColor(C.green)
    love.graphics.printf(ed.letter, px, yTitle - math.floor(4 * u), pw - pad, "right")

    -- input field + ADD
    local addW = math.floor(70 * u)
    local fieldW = pw - pad * 2 - addW - math.floor(8 * u)
    local fx = px + pad
    fillRect(fx, yField, fieldW, fieldH, C.bg, 6 * u)
    love.graphics.setColor(C.accent)
    love.graphics.rectangle("line", fx, yField, fieldW, fieldH, 6 * u, 6 * u)
    addRect(fx, yField, fieldW, fieldH, function() love.keyboard.setTextInput(true) end)

    love.graphics.setFont(F.big)
    local ty = yField + (fieldH - F.big:getHeight()) / 2
    if ed.buffer == "" then
        love.graphics.setFont(F.small)
        love.graphics.setColor(C.dim)
        love.graphics.print("type a key, then ADD", fx + 10 * u, yField + (fieldH - F.small:getHeight()) / 2)
    else
        love.graphics.setColor(C.text)
        love.graphics.print(ed.buffer, fx + 10 * u, ty)
        if ed.blink < 0.5 then
            local cx = fx + 10 * u + F.big:getWidth(ed.buffer) + 1
            love.graphics.setColor(C.text)
            love.graphics.line(cx, ty + 2, cx, ty + F.big:getHeight() - 2)
        end
    end
    if ed.buffer == "" and ed.blink < 0.5 then
        local cx = fx + 10 * u
        love.graphics.setColor(C.text)
        love.graphics.line(cx, yField + 7 * u, cx, yField + fieldH - 7 * u)
    end
    button("ADD", fx + fieldW + math.floor(8 * u), yField, addW, fieldH, C.accent, "title", doAdd, mx, my)

    -- hint / message
    love.graphics.setFont(F.small)
    local hint, hcol = "1-" .. Keymap.MAX_KEY_CHARS .. " characters, no spaces. A key already used by another letter moves here.", C.dim
    if S.msgTimer > 0 and S.msg then
        hint = S.msg
        hcol = (S.msgKind == "err") and C.err or C.green
    end
    love.graphics.setColor(hcol)
    love.graphics.printf(hint, px + pad, yHint, pw - pad * 2, "left")

    -- key rows
    if n == 0 then
        love.graphics.setFont(F.small)
        love.graphics.setColor(C.dim)
        love.graphics.printf("No keys - this letter will not decode.", px + pad, yRows + (rowH - F.small:getHeight()) / 2, pw - pad * 2, "left")
    end
    for i, key in ipairs(keys) do
        local ry = yRows + (i - 1) * pitch
        fillRect(px + pad, ry, pw - pad * 2, rowH, C.row, 6 * u)

        love.graphics.setFont(F.big)
        love.graphics.setColor(i == 1 and C.green or C.text)
        love.graphics.printf(key, px + pad + 8 * u, ry + (rowH - F.big:getHeight()) / 2, pw * 0.4, "left")

        local delW = math.floor(46 * u)
        local priW = math.floor(84 * u)
        local rx = px + pw - pad - delW - 4 * u
        local bh = rowH - math.floor(6 * u)
        local by = ry + math.floor(3 * u)
        local idx = i
        button("DEL", rx, by, delW, bh, C.red, "small", function()
            Keymap.removeKey(lang, ed.letter, idx)
            say("Removed", "ok")
        end, mx, my)
        if i == 1 then
            love.graphics.setFont(F.small)
            love.graphics.setColor(C.green)
            love.graphics.printf("PRIMARY", rx - priW - 4 * u, ry + (rowH - F.small:getHeight()) / 2, priW, "center")
        else
            button("MAKE PRIMARY", rx - priW - 14 * u - 4 * u, by, priW + 14 * u, bh, C.chip, "small", function()
                Keymap.makePrimary(lang, ed.letter, idx)
                say("Now primary (used for encoding)", "ok")
            end, mx, my)
        end
    end

    -- bottom buttons
    local bw = (pw - pad * 2 - math.floor(8 * u)) / 2
    button("CLEAR ALL", px + pad, yBtns, bw, btnH, C.red, "title", function()
        if Keymap.clearLetter(lang, ed.letter) then say("All keys removed", "ok") end
    end, mx, my)
    button("DONE", px + pad + bw + math.floor(8 * u), yBtns, bw, btnH, { 0.12, 0.42, 0.18 }, "title", closeEditor, mx, my)
end

----------------------------------------------------------------------
-- main draw
----------------------------------------------------------------------

function Settings.draw(sw, sh, mx, my)
    local u = math.max(1, math.min(sw / 360, sh / 640))
    u = math.floor(u * 4) / 4
    ensureFonts(u)
    S.u = u
    S.rects = {}

    local F = S.fonts
    local viewBottom = sh - env.tabH
    local m = math.floor(12 * u)
    local W = sw - m * 2
    local lang = langKey()

    love.graphics.push("all")

    -- header ---------------------------------------------------------
    local y = m
    love.graphics.setFont(F.title)
    love.graphics.setColor(C.text)
    love.graphics.print("KEY SETTINGS", m, y)
    local custom = Keymap.isCustom(lang)
    love.graphics.setColor(custom and C.green or C.dim)
    love.graphics.printf(custom and "CUSTOM" or "DEFAULT", m, y, W, "right")
    y = y + F.title:getHeight() + math.floor(8 * u)

    -- language chips
    local names = env.languages
    local gap = math.floor(6 * u)
    local chipH = math.floor(34 * u)
    local chipW = (W - gap * (#names - 1)) / #names
    for i, name in ipairs(names) do
        local active = (i == env.getLang())
        button(name, m + (i - 1) * (chipW + gap), y, chipW, chipH,
            active and C.accent or C.chip, "body", function()
                env.setLang(i)
                S.scroll = 0
                S.resetArmed = 0
            end, mx, my)
    end
    y = y + chipH + math.floor(8 * u)

    -- case chips + reset
    local rowH2 = math.floor(30 * u)
    local caseW = math.floor(66 * u)
    button("a - z", m, y, caseW, rowH2, S.caseSet == "lower" and C.accent or C.chip, "body",
        function() S.caseSet, S.scroll = "lower", 0 end, mx, my)
    button("A - Z", m + caseW + gap, y, caseW, rowH2, S.caseSet == "upper" and C.accent or C.chip, "body",
        function() S.caseSet, S.scroll = "upper", 0 end, mx, my)
    local armed = S.resetArmed > 0
    local resetW = math.floor(124 * u)
    button(armed and "TAP AGAIN" or "RESET LANG", m + W - resetW, y, resetW, rowH2,
        armed and { 0.9, 0.15, 0.15 } or C.red, "title", function()
            if S.resetArmed > 0 then
                S.resetArmed = 0
                if Keymap.resetLanguage(lang) then say(lang:upper() .. " reset to default", "ok")
                else say(lang:upper() .. " is already default", "info") end
            else
                S.resetArmed = 3
            end
        end, mx, my)
    y = y + rowH2 + math.floor(8 * u)

    -- info line
    love.graphics.setFont(F.small)
    local info, icol = "Tap a letter to edit its keys. The first (green) key is used when encoding.", C.dim
    if S.msgTimer > 0 and S.msg and not S.editor then
        info = S.msg
        icol = (S.msgKind == "err") and C.err or C.green
    end
    love.graphics.setColor(icol)
    love.graphics.printf(info, m, y, W, "left")
    y = y + F.small:getHeight() * 2 + math.floor(6 * u)

    -- letter list ----------------------------------------------------
    local listTop = y
    local listBottom = viewBottom - math.floor(4 * u)
    local viewH = listBottom - listTop
    local set = (S.caseSet == "lower") and Keymap.LOWER or Keymap.UPPER
    local rowH = math.floor(46 * u)
    local pitch = rowH + math.floor(4 * u)
    local contentH = #set * pitch
    S.maxScroll = math.max(0, contentH - viewH)
    S.scroll = clampScroll(S.scroll)

    love.graphics.setScissor(0, listTop, sw, viewH)
    for i, letter in ipairs(set) do
        local ry = listTop + S.scroll + (i - 1) * pitch
        if ry + rowH > listTop and ry < listBottom then
            local hover = mx >= m and mx <= m + W and my >= math.max(ry, listTop) and my <= math.min(ry + rowH, listBottom)
            fillRect(m, ry, W, rowH, hover and lighten(C.row, 0.05) or C.row, 6 * u)

            -- letter badge
            fillRect(m, ry, rowH, rowH, C.panel, 6 * u)
            love.graphics.setFont(F.big)
            love.graphics.setColor(C.text)
            love.graphics.printf(letter, m, ry + (rowH - F.big:getHeight()) / 2, rowH, "center")

            -- keys of this letter
            local keys = Keymap.keysFor(lang, letter)
            local kx = m + rowH + math.floor(12 * u)
            local limit = m + W - math.floor(54 * u)
            love.graphics.setFont(F.body)
            if #keys == 0 then
                love.graphics.setColor(C.dim)
                love.graphics.print("(no key)", kx, ry + (rowH - F.body:getHeight()) / 2)
            else
                for k, key in ipairs(keys) do
                    local kw = F.body:getWidth(key)
                    if kx + kw > limit then
                        love.graphics.setColor(C.dim)
                        love.graphics.print("...", kx, ry + (rowH - F.body:getHeight()) / 2)
                        break
                    end
                    love.graphics.setColor(k == 1 and C.green or C.text)
                    love.graphics.print(key, kx, ry + (rowH - F.body:getHeight()) / 2)
                    kx = kx + kw + math.floor(16 * u)
                end
            end

            love.graphics.setFont(F.small)
            love.graphics.setColor(C.dim)
            love.graphics.printf("EDIT >", m, ry + (rowH - F.small:getHeight()) / 2, W - math.floor(10 * u), "right")

            -- tap area clipped to the visible part of the list
            local y1, y2 = math.max(ry, listTop), math.min(ry + rowH, listBottom)
            if y2 > y1 then
                addRect(m, y1, W, y2 - y1, function() openEditor(letter) end)
            end
        end
    end
    love.graphics.setScissor()

    -- scrollbar
    if S.maxScroll > 0 then
        local thumbH = math.max(24 * u, viewH * viewH / contentH)
        local thumbY = listTop + (-S.scroll / S.maxScroll) * (viewH - thumbH)
        fillRect(sw - 5 * u, thumbY, 3 * u, thumbH, C.accent, 2 * u, 0.8)
    end

    -- editor on top of everything
    if S.editor then drawEditor(sw, viewBottom, mx, my) end

    love.graphics.pop()
end

return Settings
