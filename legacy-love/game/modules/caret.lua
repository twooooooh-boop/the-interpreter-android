local utf8 = require("utf8")
local Caret = {}

function Caret.sub(str, i, j)
    if not str or str == "" then
        return ""
    end

    local a = utf8.offset(str, i)

    if not a then
        return ""
    end

    local b

    if j then
        b = utf8.offset(str, j + 1)

        if b then
            b = b - 1
        end
    end

    return str:sub(a, b or -1)
end

function Caret.hasSelection(data)
    return data.selStart and data.selEnd and data.selStart ~= data.selEnd
end

function Caret.getSelectionRange(data)
    if not Caret.hasSelection(data) then
        return nil, nil
    end

    return math.min(data.selStart, data.selEnd),
           math.max(data.selStart, data.selEnd)
end

function Caret.deleteSelection(data)
    local s, e = Caret.getSelectionRange(data)

    if not s then
        return
    end

    local before = Caret.sub(data.text, 1, s - 1)
    local after = Caret.sub(data.text, e)

    data.text = before .. after
    data.pos = s

    data.selStart = nil
    data.selEnd = nil
end

function Caret.insert(data, char)

    if Caret.hasSelection(data) then
        Caret.deleteSelection(data)
    end

    local before = Caret.sub(data.text, 1, data.pos - 1)
    local after = Caret.sub(data.text, data.pos)

    data.text = before .. char .. after
    data.pos = data.pos + (utf8.len(char) or 1)
end

function Caret.backspace(data)

    if Caret.hasSelection(data) then
        Caret.deleteSelection(data)
        return
    end

    if data.pos <= 1 then
        return
    end

    local before = Caret.sub(data.text, 1, data.pos - 2)
    local after = Caret.sub(data.text, data.pos)

    data.text = before .. after
    data.pos = data.pos - 1
end

return Caret
