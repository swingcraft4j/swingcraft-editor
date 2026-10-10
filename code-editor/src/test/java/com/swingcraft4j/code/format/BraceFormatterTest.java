package com.swingcraft4j.code.format;

import com.swingcraft4j.code.lexer.Languages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BraceFormatterTest {

    private static final FormatOptions SPACES = new FormatOptions(4, false);

    /** Formats code, and checks that formatting what comes out changes nothing more. */
    private static String format(String languageId, String code, FormatOptions options) {
        Formatter formatter = Languages.byId(languageId).orElseThrow().formatter();
        String formatted = formatter.format(code, options);
        assertEquals(formatted, formatter.format(formatted, options), "formatting twice is formatting once");
        return formatted;
    }

    private static String format(String languageId, String code) {
        return format(languageId, code, SPACES);
    }

    private static String java(String code) {
        return format("java", code);
    }

    // ---- Indentation ----

    @Test
    void indentsEachLineByTheBracketsAroundIt() {
        assertEquals("""
                class A {
                    void run() {
                        if (x) {
                            y();
                        }
                    }
                }
                """, java("""
                    class A {
                  void run() {
                if (x) {
                            y();
                }
                        }
                }
                """));
        assertEquals("""
                foo(a, new Runnable() {
                    @Override
                    public void run() {
                        go();
                    }
                });
                int[] numbers = {
                        1,
                        2};
                """, java("""
                foo(a, new Runnable() {
                @Override
                public void run() {
                go();
                }
                });
                int[] numbers = {
                1,
                2};
                """), "the body of a class in an argument is one level in, the elements of an array two");
    }

    @Test
    void indentsALineThatGoesOnWithAStatement() {
        assertEquals("""
                String t = first
                        + second
                        + third;
                String u =
                        "a" +
                        "b";
                int x = cond
                        ? first
                        : second;
                list.stream()
                        .filter(x -> x > 0)
                        .map(x -> {
                            return x * 2;
                        })
                        .collect(toList());
                next();
                """, java("""
                String t = first
                + second
                + third;
                String u =
                "a" +
                "b";
                int x = cond
                ? first
                : second;
                list.stream()
                .filter(x -> x > 0)
                .map(x -> {
                return x * 2;
                })
                .collect(toList());
                next();
                """));
    }

    @Test
    void indentsWhatIsInsideParentheses() {
        assertEquals("""
                foo(a,
                        b);
                if (a &&
                        b) {
                    c();
                }
                public void longOne(int a,
                        int b)
                        throws IOException {
                    body();
                }
                call(
                        a,
                        b
                );
                """, java("""
                foo(a,
                b);
                if (a &&
                b) {
                c();
                }
                public void longOne(int a,
                int b)
                throws IOException {
                body();
                }
                call(
                a,
                b
                );
                """), "the brace that ends the head of a statement belongs to its first line");
        assertEquals("""
                assertEquals(List.of("a",
                                "b"),
                        other("c"
                                + "d"));
                assertEquals(List.of("a",
                        "b"), other);
                """, java("""
                assertEquals(List.of("a",
                "b"),
                other("c"
                + "d"));
                assertEquals(List.of("a",
                "b"), other);
                """), "the indents of two calls add up where the outer one has lines of its own");
        assertEquals("""
                class A {
                    private void paint(Graphics g, int row,
                                       double cell) {
                    }
                }
                """, java("""
                class A {
                private void paint(Graphics g, int row,
                                   double cell) {
                }
                }
                """), "a line that was lined up under the parenthesis stays lined up");
    }

    @Test
    void indentsTheStatementOfAnIfWithoutBraces() {
        assertEquals("""
                if (a)
                    b();
                else if (c)
                    d();
                else
                    e();
                for (String item : items)
                    for (int i = 0; i < 3; i++)
                        use(item, i);
                do {
                    x++;
                } while (x < 3);
                next();
                """, java("""
                if (a)
                b();
                else if (c)
                d();
                else
                e();
                for (String item : items)
                for (int i = 0; i < 3; i++)
                use(item, i);
                do {
                x++;
                } while (x < 3);
                next();
                """));
    }

    @Test
    void indentsTheLabelsOfASwitchAndTheirStatements() {
        assertEquals("""
                switch (x) {
                    case 1:
                        foo();
                        break;
                    case 2: {
                        bar();
                    }
                    default:
                        baz();
                }
                var v = switch (x) {
                    case 1 -> "a";
                    default -> {
                        yield "b";
                    }
                };
                """, java("""
                switch (x) {
                case 1:
                foo();
                break;
                case 2: {
                bar();
                }
                default:
                baz();
                }
                var v = switch (x) {
                case 1 -> "a";
                default -> {
                yield "b";
                }
                };
                """));
    }

    // ---- Spacing ----

    @Test
    void putsSpacesAroundOperatorsCommasAndKeywords() {
        assertEquals("int x = a + b * c - d / e % f;\n", java("int x=a+b*c-d/e%f;\n"));
        assertEquals("foo(a, b);\n", java("foo( a ,b );\n"));
        assertEquals("if (x) {\n} else {\n}\n", java("if(x){\n}else{\n}\n"));
        assertEquals("for (int i = 0; i < n; i++) { sum += i; }\n", java("for(int i=0;i<n;i++){ sum+=i; }\n"));
        assertEquals("x = -1; i++; j--; m = i - -j; boolean z = !a && b || c != d;\n",
                java("x=-1; i++; j--; m=i- -j; boolean z=!a&&b||c!=d;\n"), "a sign is not an operator between two values");
        assertEquals("String s = c ? \"a\" : \"b\";\n", java("String s=c?\"a\":\"b\";\n"));
        assertEquals("Runnable r = () -> {};\n", java("Runnable r=()->{};\n"));
        assertEquals("Object o = (Object) x; int y = (int) -z;\n", java("Object o=(Object)x;int y=(int)-z;\n"));
        assertEquals("return foo(x) + new int[]{1, 2}.length;\n", java("return foo (x)+new int[]{1,2}.length;\n"));
        assertEquals("try {\n} catch (A | B e) {\n}\n", java("try{\n}catch(A|B e){\n}\n"));
        assertEquals("int y = a<<2 | b>>>1;\n", java("int y = a<<2 | b>>>1;\n"), "where it is not sure, the space is left as written");
        assertEquals("int   x =  1;\n".replaceAll(" +", " "), java("int   x =  1;\n"), "several spaces become one");
    }

    @Test
    void tellsTypeArgumentsFromComparisons() {
        assertEquals("Map<String, List<Integer>> map = new HashMap<>();\n", java("Map<String,List<Integer>> map=new HashMap< >();\n"));
        assertEquals("public <T> T get(Class<? extends T> type) {\n}\n", java("public <T> T get(Class< ? extends T > type){\n}\n"));
        assertEquals("Foo.<String>bar();\n", java("Foo.<String>bar();\n"));
        assertEquals("if (a < b && b > 0) {\n}\n", java("if(a<b&&b>0){\n}\n"));
        assertEquals("boolean less = i < n; return a < b ? a : b;\n", java("boolean less=i<n; return a<b?a:b;\n"));
    }

    // ---- What is kept ----

    @Test
    void neverChangesStringsAndComments() {
        assertEquals("""
                class A {
                    /**
                     * Doc   of  it.
                     */
                    String text = \"""
                    keep   this=as,it(is
                  \""";
                    /* block
                         comment */
                    String s = "a=b,c" + 'x'; //done
                    int a = 1;      // one
                    int abc = 2;    // two
                }
                """, java("""
                class A {
                /**
                 * Doc   of  it.
                 */
                String text = \"""
                    keep   this=as,it(is
                  \""";
                  /* block
                       comment */
                String s="a=b,c"+'x';//done
                int a = 1;      // one
                int abc = 2;    // two
                }
                """), "the lines of a block comment move with its first line; those of a string do not move");
    }

    @Test
    void indentsACommentAsTheCodeBelowIt() {
        assertEquals("""
                class A {
                    // above a method
                    void run() {
                        return builder
                                // above a call
                                .build();
                        // at the end
                    }
                }
                """, java("""
                class A {
                // above a method
                void run() {
                return builder
                // above a call
                .build();
                // at the end
                }
                }
                """));
    }

    @Test
    void keepsEveryLineBreakAndReducesBlankLines() {
        assertEquals("a();\n\n\nb(c,\n        d\n);\n\ne();", java("a();\n\n\n\n\n\nb(c,\nd\n);\n  \ne();"));
        assertEquals("if (x) {\r\n\ty();\r\n}\r\n", format("java", "if(x){\r\n        y();\r\n}\r\n", new FormatOptions(4, true)),
                "tabs as the options ask for them, and the ends of the lines as they are");
        String formatted = "class A {\n    int x = 1;\n}\n";
        assertSame(formatted, java(formatted), "code that is formatted already is returned as it is");
    }

    @Test
    void formatsCodeThatIsHalfTyped() {
        assertEquals("""
                class Half {
                    void run() {
                        foo(a,
                                if (x) {
                                    y = (1 +
                                }
                    }
                    void other() {
                        int z =;
                        String s = "open
                        call(]
                    }
                }
                }
                extra();
                """, java("""
                class Half {
                void run() {
                foo(a,
                if (x) {
                y = (1 +
                }
                }
                void other() {
                int z = ;
                String s = "open
                call(]
                }
                }
                }
                extra();
                """), "a bracket that is not closed is given up at the one that closes around it");
        assertEquals("}\n}\nnext();\n", java("    }\n  }\n    next();\n"), "as are lines taken from the middle of a file");
    }

    // ---- The languages ----

    @Test
    void javaScript() {
        assertEquals("""
                import {a, b} from 'x';
                const f = async (x, y) => {
                    if (x === y) {return x / 2}
                    const re = /a+b=c[/"]/g, d = x / y / 2;
                    let o = {a: 1, b: [1, 2, 3], c: {d: null}};
                    return x?.y ?? z ? a : b;
                };
                promise
                    .then(r => r.json())
                    .catch(e => {
                        throw e
                    })
                const s = `a ${b+c}
                   keep`;
                switch (a) {
                    case 1:
                        b()
                        break
                    default:
                        c()
                }
                """, format("javascript", """
                import {a,b} from 'x';
                const f=async(x,y)=>{
                if(x===y){return x/2}
                const re=/a+b=c[/"]/g, d=x/y/2;
                let o={a:1,b:[1,2,3],c:{d:null}};
                return x?.y??z?a:b;
                };
                promise
                .then(r=>r.json())
                .catch(e=>{
                throw e
                })
                const s=`a ${b+c}
                   keep`;
                switch(a){
                case 1:
                b()
                break
                default:
                c()
                }
                """), "a regular expression between slashes is left alone, and so is a template");
        String jsx = "function App() {\nreturn <div className=\"a\">\n  <b>x=1,y</b>\n</div>;\n}\n";
        assertSame(jsx, format("javascript", jsx), "a file with JSX in it is not formatted");
        assertEquals("el.innerHTML = '<b>x</b>'\n", format("javascript", "el.innerHTML='<b>x</b>'\n"), "a tag in a string is no JSX");
    }

    @Test
    void typeScript() {
        assertEquals("""
                function id<T>(x: T): Array<T> {
                    const m: Map<string, number> = new Map<string, number>();
                    return x < y ? [x] : [y];
                }
                interface A {b?: string; c(d: number): void}
                type F = (a: number) => void;
                """, format("typescript", """
                function id<T>(x:T):Array<T>{
                const m:Map<string,number>=new Map<string,number>();
                return x<y?[x]:[y];
                }
                interface A{b?:string;c(d:number):void}
                type F=(a:number)=>void;
                """));
    }

    @Test
    void cAndCpp() {
        assertEquals("""
                #include <stdio.h>
                #define MAX(a,b) \\
                    ((a)>(b)?(a):(b))
                int main(int argc, char **argv) {
                    char *s = argv[1]; int *p = &x;
                    if (p->next == NULL) return -1;
                    for (i = 0; i < n; i++)
                        total += a[i]*b[i];
                    switch (c) {
                        case 'a':
                            x = 1; break;
                    }
                    return (int)x;
                }
                """, format("c", """
                #include <stdio.h>
                #define MAX(a,b) \\
                    ((a)>(b)?(a):(b))
                int main(int argc,char **argv){
                char *s=argv[1];int *p=&x;
                if(p->next==NULL)return -1;
                for(i=0;i<n;i++)
                total+=a[i]*b[i];
                switch(c){
                case 'a':
                x=1;break;
                }
                return (int)x;
                }
                """), "preprocessor lines are copied, and the space at a star that may point is left as written");
        assertEquals("""
                template<typename T>
                class Foo: public Bar {
                public:
                    std::vector<std::string> names{"a", "b"};
                    void run() {std::cout<<x<<std::endl;}
                private:
                    int a = b < c ? 1 : 2;
                };
                """, format("cpp", """
                template<typename T>
                class Foo:public Bar{
                public:
                std::vector<std::string> names{"a","b"};
                void run(){std::cout<<x<<std::endl;}
                private:
                int a=b<c?1:2;
                };
                """));
    }

    @Test
    void cSharp() {
        assertEquals("""
                namespace A
                {
                    public class Foo
                    {
                        private Dictionary<string, List<int>> map = new Dictionary<string, List<int>>();
                        public int? Bar { get; set; }
                        public void Run()
                        {
                            var x = list.Where(i => i > 0).ToList();
                            foreach (var i in x) {Console.WriteLine(i);}
                            var y = a ?? b; var z = c ? d : e;
                        }
                    }
                }
                """, format("csharp", """
                namespace A
                {
                public class Foo
                {
                private Dictionary<string,List<int>> map=new Dictionary<string,List<int>>();
                public int? Bar { get; set; }
                public void Run()
                {
                var x=list.Where(i=>i>0).ToList();
                foreach(var i in x){Console.WriteLine(i);}
                var y=a??b;var z=c?d:e;
                }
                }
                }
                """));
    }

    @Test
    void kotlin() {
        assertEquals("""
                class Foo(val a: Int, val b: String?): Bar() {
                    fun run(x: List<Int>): Map<String, Int> {
                        val y = x.map {it * 2}.filter {it > 3}
                        val z = b ?: "none"
                        when (a) {
                            1 -> foo()
                            else -> bar()
                        }
                        if (a < b)
                            println("$a ${map["k=v"]} x")
                        val list = items
                            .filter { it.ok }
                            .map {
                                it.name
                            }
                        return mapOf("a" to (1))
                    }
                }
                """, format("kotlin", """
                class Foo(val a:Int,val b:String?):Bar(){
                fun run(x:List<Int>):Map<String,Int>{
                val y=x.map{it*2}.filter{it>3}
                val z=b?:"none"
                when(a){
                1->foo()
                else->bar()
                }
                if(a<b)
                println("$a ${map["k=v"]} x")
                val list = items
                .filter { it.ok }
                .map {
                it.name
                }
                return mapOf("a" to (1))
                }
                }
                """), "a string with a template that has a string in it is left alone, though the lexer splits it");
    }

    @Test
    void go() {
        assertEquals("""
                package main

                type Point struct {
                \tX    int    `json:"x"`
                \tName string // the name
                }

                func (s *Server) Run(a int, b string) (int, error) {
                \tx := []int{1, 2, 3}
                \tm := map[string]int{}
                \tvar e interface{}
                \tp := Point{
                \t\tX: 1,
                \t}
                \tif err != nil {
                \t\treturn 0, err
                \t}
                \tswitch a {
                \tcase 1:
                \t\tfoo(x[1:2])
                \tdefault:
                \t\tbar()
                \t}
                \tfor i := 0; i < n; i++ {
                \t\tch<-i
                \t}

                \treturn a + b, nil
                }
                """, format("go", """
                package main


                type Point struct {
                X    int    `json:"x"`
                Name string // the name
                }

                func (s *Server) Run(a int,b string) (int,error){
                x:=[]int{1,2,3}
                m:=map[string]int{}
                var e interface{}
                p := Point{
                X: 1,
                }
                if err!=nil{
                return 0,err
                }
                switch a{
                case 1:
                foo(x[1:2])
                default:
                bar()
                }
                for i:=0;i<n;i++{
                ch<-i
                }


                return a+b,nil
                }
                """), "with tabs, one blank line, the labels under the switch, and the columns of a struct as they are");
    }

    @Test
    void rust() {
        assertEquals("""
                impl<T> Foo<T> {
                    fn run(&self, x: &mut Vec<u8>) -> Result<i32, Error> {
                        let y = x.iter().map(|a| a + 1).collect::<Vec<_>>();
                        match y {
                            Some(v) => v,
                            None => 0,
                        }
                        if a < b && c { return Ok(1); }
                        let z = *p + &q;
                        let l: &'static str = "x";
                        let g = move || 1;
                        Ok(a?)
                    }
                }
                """, format("rust", """
                impl<T> Foo<T>{
                fn run(&self,x:&mut Vec<u8>)->Result<i32,Error>{
                let y=x.iter().map(|a| a+1).collect::<Vec<_>>();
                match y{
                Some(v)=>v,
                None=>0,
                }
                if a<b&&c{ return Ok(1); }
                let z=*p+&q;
                let l:&'static str="x";
                let g=move || 1;
                Ok(a?)
                }
                }
                """));
    }

    @Test
    void groovy() {
        assertEquals("""
                dependencies {
                    implementation 'a:b:1'
                    testImplementation group: 'x', name: 'y'
                }
                def m = [a: 1, b: 2]
                def r = x =~ /a+b=c/
                list.each {println it}
                def v = a ?: b
                """, format("groovy", """
                dependencies{
                implementation 'a:b:1'
                testImplementation group:'x',name:'y'
                }
                def m=[a:1,b:2]
                def r=x =~ /a+b=c/
                list.each{println it}
                def v=a?:b
                """));
    }

    @Test
    void css() {
        assertEquals("""
                @media (max-width:100px) {
                    a:hover, .b>c {color: red; background: url(data:image/png;base64,AA==); margin: 0 auto}
                    .x {
                        transition: opacity 1s,
                            transform 2s;
                        --main-color: #fff;
                    }
                }
                ul li:first-child
                {
                    color: blue
                }
                .x::before {content: "a:b;c"}
                """, format("css", """
                @media (max-width:100px){
                a:hover,.b>c{color:red;background:url(data:image/png;base64,AA==) ;margin:0 auto}
                .x{
                transition:opacity 1s,
                transform 2s;
                --main-color:#fff;
                }
                }
                ul li:first-child
                {
                color : blue
                }
                .x::before{content:"a:b;c"}
                """), "only the colon of a declaration gets a space, and what is in parentheses stays as it is");
    }
}
