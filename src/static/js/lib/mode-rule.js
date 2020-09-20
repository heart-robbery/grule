define("ace/mode/rule_highlight_rules", ["require", "exports", "module", "ace/lib/oop", "ace/mode/text_highlight_rules"],
    function(e, t, n) {
        "use strict";
        var r = e("../lib/oop"),
            i = e("./text_highlight_rules").TextHighlightRules,
            s = function() {
                var e = "决策id|决策名|决策描述|返回属性|策略定义|策略名|规则定义|规则名|属性定义|拒绝|通过|操作|人工审核|if|def",
                    t = "true|false|null",
                    n = "contains|count|first|last|max|min|sum",
                    r = "Integer|Long|",
                    i = this.createKeywordMapper({
                            "support.function": n,
                            keyword: e,
                            "constant.language": t,
                            "storage.type": r
                        },
                        "identifier", !0);
                this.$rules = {
                    start: [
                        {
                            token: "comment",
                            regex: "//.*$"
                        },
                        {
                            token: "comment",
                            start: "/\\*",
                            end: "\\*/"
                        },
                        {
                            token: "string",
                            regex: '".*?"'
                        },
                        {
                            token: "string",
                            regex: "'.*?'"
                        },
                        {
                            token: "string",
                            regex: "'''.*?'''"
                        },
                        {
                            token: "string",
                            regex: '""".*?"""'
                        },
                        {
                            token: "constant.numeric",
                            regex: "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"
                        },
                        {
                            token: i,
                            regex: "[a-zA-Z_$][a-zA-Z0-9_$]*\\b"
                        },
                        {
                            token: "keyword.operator",
                            regex: "\\+|\\-|\\/|\\/\\/|%|<@>|@>|<@|&|\\^|~|<|>|<=|=>|==|!=|<>|=|in|contains"
                        },
                        {
                            token: "paren.lparen",
                            regex: "[\\(]"
                        },
                        {
                            token: "paren.rparen",
                            regex: "[\\)]"
                        },
                        {
                            token: "text",
                            regex: "\\s+"
                        }]
                },
                    this.normalizeRules()
            };
            r.inherits(s, i),
            t.RuleHighlightRules = s
    }),
    define("ace/mode/rule", ["require", "exports", "module", "ace/lib/oop", "ace/mode/text", "ace/mode/rule_highlight_rules"],
        function(e, t, n) {
            "use strict";
            var r = e("../lib/oop"),
                i = e("./text").Mode,
                s = e("./rule_highlight_rules").RuleHighlightRules,
                o = function() {
                    this.HighlightRules = s,
                        this.$behaviour = this.$defaultBehaviour
                };
            r.inherits(o, i),
                function() {
                    this.lineCommentStart = "--",
                        this.$id = "ace/mode/rule"
                        // this.snippetFileId = "ace/snippets/rule"
                }.call(o.prototype),
                t.Mode = o
        }); (function() {
    window.require(["ace/mode/rule"],
        function(m) {
            if (typeof module == "object" && typeof exports == "object" && module) {
                module.exports = m;
            }
        });
})();