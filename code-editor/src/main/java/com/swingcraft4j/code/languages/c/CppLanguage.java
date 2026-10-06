package com.swingcraft4j.code.languages.c;

import com.swingcraft4j.code.lexer.RuleLanguage;

/** C++: C with its own keywords. Raw string literals are read as ordinary strings. */
public final class CppLanguage extends RuleLanguage {

    public CppLanguage() {
        super(CLanguage.rules("cpp", "C++")
                .extensions("cpp", "cc", "cxx", "hpp", "hh", "hxx")
                .keywords("alignas", "alignof", "catch", "class", "concept", "consteval", "constexpr", "constinit",
                        "const_cast", "co_await", "co_return", "co_yield", "decltype", "delete", "dynamic_cast",
                        "explicit", "export", "final", "friend", "import", "module", "mutable", "namespace", "new",
                        "noexcept", "operator", "override", "private", "protected", "public", "reinterpret_cast",
                        "requires", "static_assert", "static_cast", "template", "this", "thread_local", "throw",
                        "try", "typeid", "typename", "using", "virtual")
                .types("char8_t", "char16_t", "char32_t", "string", "vector", "map", "set")
                .literals("nullptr"));
    }
}
