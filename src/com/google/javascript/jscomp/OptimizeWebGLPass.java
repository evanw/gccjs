package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.*;
import com.google.javascript.rhino.jstype.JSType;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.*;

class OptimizeWebGLPass extends NodeTraversal.AbstractPostOrderCallback implements CompilerPass {
  // Annotate strings containing GLSL with "/** @const {GLSL} */"
  static final String GLSL_TYPEDEF = "GLSL";

  static final DiagnosticType UNSUPPORTED_CONST_GLSL_SYNTAX = DiagnosticType.warning(
    "JSC_UNSUPPORTED_CONST_GLSL_SYNTAX",
    "Unsupported syntax after \"@const '{" + GLSL_TYPEDEF + "}'\". " +
    "Only variable and assignment statements are supported.");

  AbstractCompiler compiler;
  JSType contextType;

  // Taken from "console.log(WebGLRenderingContext.prototype)"
  static final Map<String, Integer> constants = new HashMap<String, Integer>() {{
    put("DEPTH_BUFFER_BIT", 256);
    put("STENCIL_BUFFER_BIT", 1024);
    put("COLOR_BUFFER_BIT", 16384);
    put("POINTS", 0);
    put("LINES", 1);
    put("LINE_LOOP", 2);
    put("LINE_STRIP", 3);
    put("TRIANGLES", 4);
    put("TRIANGLE_STRIP", 5);
    put("TRIANGLE_FAN", 6);
    put("ZERO", 0);
    put("ONE", 1);
    put("SRC_COLOR", 768);
    put("ONE_MINUS_SRC_COLOR", 769);
    put("SRC_ALPHA", 770);
    put("ONE_MINUS_SRC_ALPHA", 771);
    put("DST_ALPHA", 772);
    put("ONE_MINUS_DST_ALPHA", 773);
    put("DST_COLOR", 774);
    put("ONE_MINUS_DST_COLOR", 775);
    put("SRC_ALPHA_SATURATE", 776);
    put("FUNC_ADD", 32774);
    put("BLEND_EQUATION", 32777);
    put("BLEND_EQUATION_RGB", 32777);
    put("BLEND_EQUATION_ALPHA", 34877);
    put("FUNC_SUBTRACT", 32778);
    put("FUNC_REVERSE_SUBTRACT", 32779);
    put("BLEND_DST_RGB", 32968);
    put("BLEND_SRC_RGB", 32969);
    put("BLEND_DST_ALPHA", 32970);
    put("BLEND_SRC_ALPHA", 32971);
    put("CONSTANT_COLOR", 32769);
    put("ONE_MINUS_CONSTANT_COLOR", 32770);
    put("CONSTANT_ALPHA", 32771);
    put("ONE_MINUS_CONSTANT_ALPHA", 32772);
    put("BLEND_COLOR", 32773);
    put("ARRAY_BUFFER", 34962);
    put("ELEMENT_ARRAY_BUFFER", 34963);
    put("ARRAY_BUFFER_BINDING", 34964);
    put("ELEMENT_ARRAY_BUFFER_BINDING", 34965);
    put("STREAM_DRAW", 35040);
    put("STATIC_DRAW", 35044);
    put("DYNAMIC_DRAW", 35048);
    put("BUFFER_SIZE", 34660);
    put("BUFFER_USAGE", 34661);
    put("CURRENT_VERTEX_ATTRIB", 34342);
    put("FRONT", 1028);
    put("BACK", 1029);
    put("FRONT_AND_BACK", 1032);
    put("TEXTURE_2D", 3553);
    put("CULL_FACE", 2884);
    put("BLEND", 3042);
    put("DITHER", 3024);
    put("STENCIL_TEST", 2960);
    put("DEPTH_TEST", 2929);
    put("SCISSOR_TEST", 3089);
    put("POLYGON_OFFSET_FILL", 32823);
    put("SAMPLE_ALPHA_TO_COVERAGE", 32926);
    put("SAMPLE_COVERAGE", 32928);
    put("NO_ERROR", 0);
    put("INVALID_ENUM", 1280);
    put("INVALID_VALUE", 1281);
    put("INVALID_OPERATION", 1282);
    put("OUT_OF_MEMORY", 1285);
    put("CW", 2304);
    put("CCW", 2305);
    put("LINE_WIDTH", 2849);
    put("ALIASED_POINT_SIZE_RANGE", 33901);
    put("ALIASED_LINE_WIDTH_RANGE", 33902);
    put("CULL_FACE_MODE", 2885);
    put("FRONT_FACE", 2886);
    put("DEPTH_RANGE", 2928);
    put("DEPTH_WRITEMASK", 2930);
    put("DEPTH_CLEAR_VALUE", 2931);
    put("DEPTH_FUNC", 2932);
    put("STENCIL_CLEAR_VALUE", 2961);
    put("STENCIL_FUNC", 2962);
    put("STENCIL_FAIL", 2964);
    put("STENCIL_PASS_DEPTH_FAIL", 2965);
    put("STENCIL_PASS_DEPTH_PASS", 2966);
    put("STENCIL_REF", 2967);
    put("STENCIL_VALUE_MASK", 2963);
    put("STENCIL_WRITEMASK", 2968);
    put("STENCIL_BACK_FUNC", 34816);
    put("STENCIL_BACK_FAIL", 34817);
    put("STENCIL_BACK_PASS_DEPTH_FAIL", 34818);
    put("STENCIL_BACK_PASS_DEPTH_PASS", 34819);
    put("STENCIL_BACK_REF", 36003);
    put("STENCIL_BACK_VALUE_MASK", 36004);
    put("STENCIL_BACK_WRITEMASK", 36005);
    put("VIEWPORT", 2978);
    put("SCISSOR_BOX", 3088);
    put("COLOR_CLEAR_VALUE", 3106);
    put("COLOR_WRITEMASK", 3107);
    put("UNPACK_ALIGNMENT", 3317);
    put("PACK_ALIGNMENT", 3333);
    put("MAX_TEXTURE_SIZE", 3379);
    put("MAX_VIEWPORT_DIMS", 3386);
    put("SUBPIXEL_BITS", 3408);
    put("RED_BITS", 3410);
    put("GREEN_BITS", 3411);
    put("BLUE_BITS", 3412);
    put("ALPHA_BITS", 3413);
    put("DEPTH_BITS", 3414);
    put("STENCIL_BITS", 3415);
    put("POLYGON_OFFSET_UNITS", 10752);
    put("POLYGON_OFFSET_FACTOR", 32824);
    put("TEXTURE_BINDING_2D", 32873);
    put("SAMPLE_BUFFERS", 32936);
    put("SAMPLES", 32937);
    put("SAMPLE_COVERAGE_VALUE", 32938);
    put("SAMPLE_COVERAGE_INVERT", 32939);
    put("COMPRESSED_TEXTURE_FORMATS", 34467);
    put("DONT_CARE", 4352);
    put("FASTEST", 4353);
    put("NICEST", 4354);
    put("GENERATE_MIPMAP_HINT", 33170);
    put("BYTE", 5120);
    put("UNSIGNED_BYTE", 5121);
    put("SHORT", 5122);
    put("UNSIGNED_SHORT", 5123);
    put("INT", 5124);
    put("UNSIGNED_INT", 5125);
    put("FLOAT", 5126);
    put("HALF_FLOAT_OES", 36193);
    put("DEPTH_COMPONENT", 6402);
    put("ALPHA", 6406);
    put("RGB", 6407);
    put("RGBA", 6408);
    put("LUMINANCE", 6409);
    put("LUMINANCE_ALPHA", 6410);
    put("UNSIGNED_SHORT_4_4_4_4", 32819);
    put("UNSIGNED_SHORT_5_5_5_1", 32820);
    put("UNSIGNED_SHORT_5_6_5", 33635);
    put("FRAGMENT_SHADER", 35632);
    put("VERTEX_SHADER", 35633);
    put("MAX_VERTEX_ATTRIBS", 34921);
    put("MAX_VERTEX_UNIFORM_VECTORS", 36347);
    put("MAX_VARYING_VECTORS", 36348);
    put("MAX_COMBINED_TEXTURE_IMAGE_UNITS", 35661);
    put("MAX_VERTEX_TEXTURE_IMAGE_UNITS", 35660);
    put("MAX_TEXTURE_IMAGE_UNITS", 34930);
    put("MAX_FRAGMENT_UNIFORM_VECTORS", 36349);
    put("SHADER_TYPE", 35663);
    put("DELETE_STATUS", 35712);
    put("LINK_STATUS", 35714);
    put("VALIDATE_STATUS", 35715);
    put("ATTACHED_SHADERS", 35717);
    put("ACTIVE_UNIFORMS", 35718);
    put("ACTIVE_ATTRIBUTES", 35721);
    put("SHADING_LANGUAGE_VERSION", 35724);
    put("CURRENT_PROGRAM", 35725);
    put("NEVER", 512);
    put("LESS", 513);
    put("EQUAL", 514);
    put("LEQUAL", 515);
    put("GREATER", 516);
    put("NOTEQUAL", 517);
    put("GEQUAL", 518);
    put("ALWAYS", 519);
    put("KEEP", 7680);
    put("REPLACE", 7681);
    put("INCR", 7682);
    put("DECR", 7683);
    put("INVERT", 5386);
    put("INCR_WRAP", 34055);
    put("DECR_WRAP", 34056);
    put("VENDOR", 7936);
    put("RENDERER", 7937);
    put("VERSION", 7938);
    put("NEAREST", 9728);
    put("LINEAR", 9729);
    put("NEAREST_MIPMAP_NEAREST", 9984);
    put("LINEAR_MIPMAP_NEAREST", 9985);
    put("NEAREST_MIPMAP_LINEAR", 9986);
    put("LINEAR_MIPMAP_LINEAR", 9987);
    put("TEXTURE_MAG_FILTER", 10240);
    put("TEXTURE_MIN_FILTER", 10241);
    put("TEXTURE_WRAP_S", 10242);
    put("TEXTURE_WRAP_T", 10243);
    put("TEXTURE", 5890);
    put("TEXTURE_CUBE_MAP", 34067);
    put("TEXTURE_BINDING_CUBE_MAP", 34068);
    put("TEXTURE_CUBE_MAP_POSITIVE_X", 34069);
    put("TEXTURE_CUBE_MAP_NEGATIVE_X", 34070);
    put("TEXTURE_CUBE_MAP_POSITIVE_Y", 34071);
    put("TEXTURE_CUBE_MAP_NEGATIVE_Y", 34072);
    put("TEXTURE_CUBE_MAP_POSITIVE_Z", 34073);
    put("TEXTURE_CUBE_MAP_NEGATIVE_Z", 34074);
    put("MAX_CUBE_MAP_TEXTURE_SIZE", 34076);
    put("TEXTURE0", 33984);
    put("TEXTURE1", 33985);
    put("TEXTURE2", 33986);
    put("TEXTURE3", 33987);
    put("TEXTURE4", 33988);
    put("TEXTURE5", 33989);
    put("TEXTURE6", 33990);
    put("TEXTURE7", 33991);
    put("TEXTURE8", 33992);
    put("TEXTURE9", 33993);
    put("TEXTURE10", 33994);
    put("TEXTURE11", 33995);
    put("TEXTURE12", 33996);
    put("TEXTURE13", 33997);
    put("TEXTURE14", 33998);
    put("TEXTURE15", 33999);
    put("TEXTURE16", 34000);
    put("TEXTURE17", 34001);
    put("TEXTURE18", 34002);
    put("TEXTURE19", 34003);
    put("TEXTURE20", 34004);
    put("TEXTURE21", 34005);
    put("TEXTURE22", 34006);
    put("TEXTURE23", 34007);
    put("TEXTURE24", 34008);
    put("TEXTURE25", 34009);
    put("TEXTURE26", 34010);
    put("TEXTURE27", 34011);
    put("TEXTURE28", 34012);
    put("TEXTURE29", 34013);
    put("TEXTURE30", 34014);
    put("TEXTURE31", 34015);
    put("ACTIVE_TEXTURE", 34016);
    put("REPEAT", 10497);
    put("CLAMP_TO_EDGE", 33071);
    put("MIRRORED_REPEAT", 33648);
    put("FLOAT_VEC2", 35664);
    put("FLOAT_VEC3", 35665);
    put("FLOAT_VEC4", 35666);
    put("INT_VEC2", 35667);
    put("INT_VEC3", 35668);
    put("INT_VEC4", 35669);
    put("BOOL", 35670);
    put("BOOL_VEC2", 35671);
    put("BOOL_VEC3", 35672);
    put("BOOL_VEC4", 35673);
    put("FLOAT_MAT2", 35674);
    put("FLOAT_MAT3", 35675);
    put("FLOAT_MAT4", 35676);
    put("SAMPLER_2D", 35678);
    put("SAMPLER_CUBE", 35680);
    put("VERTEX_ATTRIB_ARRAY_ENABLED", 34338);
    put("VERTEX_ATTRIB_ARRAY_SIZE", 34339);
    put("VERTEX_ATTRIB_ARRAY_STRIDE", 34340);
    put("VERTEX_ATTRIB_ARRAY_TYPE", 34341);
    put("VERTEX_ATTRIB_ARRAY_NORMALIZED", 34922);
    put("VERTEX_ATTRIB_ARRAY_POINTER", 34373);
    put("VERTEX_ATTRIB_ARRAY_BUFFER_BINDING", 34975);
    put("COMPILE_STATUS", 35713);
    put("LOW_FLOAT", 36336);
    put("MEDIUM_FLOAT", 36337);
    put("HIGH_FLOAT", 36338);
    put("LOW_INT", 36339);
    put("MEDIUM_INT", 36340);
    put("HIGH_INT", 36341);
    put("FRAMEBUFFER", 36160);
    put("RENDERBUFFER", 36161);
    put("RGBA4", 32854);
    put("RGB5_A1", 32855);
    put("RGB565", 36194);
    put("DEPTH_COMPONENT16", 33189);
    put("STENCIL_INDEX", 6401);
    put("STENCIL_INDEX8", 36168);
    put("DEPTH_STENCIL", 34041);
    put("RENDERBUFFER_WIDTH", 36162);
    put("RENDERBUFFER_HEIGHT", 36163);
    put("RENDERBUFFER_INTERNAL_FORMAT", 36164);
    put("RENDERBUFFER_RED_SIZE", 36176);
    put("RENDERBUFFER_GREEN_SIZE", 36177);
    put("RENDERBUFFER_BLUE_SIZE", 36178);
    put("RENDERBUFFER_ALPHA_SIZE", 36179);
    put("RENDERBUFFER_DEPTH_SIZE", 36180);
    put("RENDERBUFFER_STENCIL_SIZE", 36181);
    put("FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE", 36048);
    put("FRAMEBUFFER_ATTACHMENT_OBJECT_NAME", 36049);
    put("FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL", 36050);
    put("FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE", 36051);
    put("COLOR_ATTACHMENT0", 36064);
    put("DEPTH_ATTACHMENT", 36096);
    put("STENCIL_ATTACHMENT", 36128);
    put("DEPTH_STENCIL_ATTACHMENT", 33306);
    put("NONE", 0);
    put("FRAMEBUFFER_COMPLETE", 36053);
    put("FRAMEBUFFER_INCOMPLETE_ATTACHMENT", 36054);
    put("FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT", 36055);
    put("FRAMEBUFFER_INCOMPLETE_DIMENSIONS", 36057);
    put("FRAMEBUFFER_UNSUPPORTED", 36061);
    put("FRAMEBUFFER_BINDING", 36006);
    put("RENDERBUFFER_BINDING", 36007);
    put("MAX_RENDERBUFFER_SIZE", 34024);
    put("INVALID_FRAMEBUFFER_OPERATION", 1286);
    put("UNPACK_FLIP_Y_WEBGL", 37440);
    put("UNPACK_PREMULTIPLY_ALPHA_WEBGL", 37441);
    put("CONTEXT_LOST_WEBGL", 37442);
    put("UNPACK_COLORSPACE_CONVERSION_WEBGL", 37443);
    put("BROWSER_DEFAULT_WEBGL", 37444);
  }};

  OptimizeWebGLPass(AbstractCompiler compiler) {
    this.compiler = compiler;
    contextType = compiler.getTypeRegistry().getType("WebGLRenderingContext");
    Preconditions.checkNotNull(contextType);
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
    minifyGLSL();
  }

  @Override
  public void visit(NodeTraversal t, Node node, Node parent) {
    JSDocInfo info = node.getJSDocInfo();
    if (info != null && info.isConstant() && info.getType() != null) {
      Node root = info.getType().getRoot();
      if (root.isString() && root.getString().equals(GLSL_TYPEDEF)) {
        handleGLSL(t, node);
        return;
      }
    }
    if (node.isGetProp()) {
      replaceWebGLRenderingContextProperty(node, parent);
    }
  }

  HashMap<Node, String> glslStringConstants = new HashMap<Node, String>();

  void handleGLSL(NodeTraversal t, Node node) {
    if (node.isVar()) {
      for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
        Preconditions.checkState(c.isName());
        Node value = c.getFirstChild();
        if (value != null && value.getJSType().isString()) {
          glslStringConstants.put(value, value.getString());
        }
      }
    } else if (node.isAssign()) {
      Node value = node.getLastChild();
      if (value != null && value.getJSType().isString()) {
        glslStringConstants.put(value, value.getString());
      }
    } else {
      compiler.report(t.makeError(node, UNSUPPORTED_CONST_GLSL_SYNTAX));
    }
  }

  void replaceWebGLRenderingContextProperty(Node node, Node parent) {
    JSType type = node.getFirstChild().getJSType().restrictByNotNullOrUndefined();
    if (type == contextType) {
      String name = node.getLastChild().getString();
      Integer constant = constants.get(name);
      if (constant != null) {
        parent.replaceChild(node, NodeUtil.numberNode(constant, node));
        compiler.reportCodeChange();
      }
    }
  }

  // From the GLSL specification version 1.20:
  // http://www.opengl.org/registry/doc/GLSLangSpec.Full.1.20.8.pdf
  static final HashSet<String> GLSL_KEYWORDS = new HashSet<String>() {{
    // Keywords
    add("attribute");
    add("const");
    add("uniform");
    add("varying");
    add("break");
    add("continue");
    add("do");
    add("for");
    add("while");
    add("if");
    add("else");
    add("in");
    add("out");
    add("inout");
    add("float");
    add("int");
    add("void");
    add("bool");
    add("true");
    add("false");
    add("lowp");
    add("mediump");
    add("highp");
    add("precision");
    add("invariant");
    add("discard");
    add("return");
    add("mat2");
    add("mat3");
    add("mat4");
    add("vec2");
    add("vec3");
    add("vec4");
    add("ivec2");
    add("ivec3");
    add("ivec4");
    add("bvec2");
    add("bvec3");
    add("bvec4");
    add("sampler2D");
    add("samplerCube");
    add("struct");

    // Reserved
    add("asm");
    add("class");
    add("union");
    add("enum");
    add("typedef");
    add("template");
    add("this");
    add("packed");
    add("goto");
    add("switch");
    add("default");
    add("inline");
    add("noinline");
    add("volatile");
    add("public");
    add("static");
    add("extern");
    add("external");
    add("interface");
    add("flat");
    add("long");
    add("short");
    add("double");
    add("half");
    add("fixed");
    add("unsigned");
    add("superp");
    add("input");
    add("output");
    add("hvec2");
    add("hvec3");
    add("hvec4");
    add("dvec2");
    add("dvec3");
    add("dvec4");
    add("fvec2");
    add("fvec3");
    add("fvec4");
    add("sampler1D");
    add("sampler3D");
    add("sampler1DShadow");
    add("sampler2DShadow");
    add("sampler2DRect");
    add("sampler3DRect");
    add("sampler2DRectShadow");
    add("sizeof");
    add("cast");
    add("namespace");
    add("using");

    // Built-in functions
    add("radians");
    add("degrees");
    add("sin");
    add("cos");
    add("tan");
    add("asin");
    add("acos");
    add("atan");
    add("pow");
    add("exp");
    add("log");
    add("exp2");
    add("log2");
    add("sqrt");
    add("inversesqrt");
    add("abs");
    add("sign");
    add("floor");
    add("ceil");
    add("fract");
    add("mod");
    add("min");
    add("max");
    add("clamp");
    add("mix");
    add("step");
    add("smoothstep");
    add("length");
    add("distance");
    add("dot");
    add("cross");
    add("normalize");
    add("faceforward");
    add("reflect");
    add("refract");
    add("matrixCompMult");
    add("lessThan");
    add("lessThanEqual");
    add("greaterThan");
    add("greaterThanEqual");
    add("equal");
    add("notEqual");
    add("any");
    add("all");
    add("not");
    add("texture2D");
    add("texture2DProj");
    add("texture2DLod");
    add("texture2DProjLod");
    add("texture2DCube");
    add("texture2DCubeLod");

    // Extra
    add("main");
    add("defined");
    add("dFdx");
    add("dFdy");
    add("require");
    add("warn");
    add("enable");
    add("disable");
  }};

  // Both /* style */ and // comments
  static final Pattern PREPROCESSOR_COMMAND = Pattern.compile("^[ \t]*#");

  // Both /* style */ and // comments
  static final Pattern GLSL_COMMENTS = Pattern.compile(
    "(\\/\\*(?:[^\\*]|\\*[^\\/])*\\*\\/|\\/\\/[^\\n]*)");

  // This includes # or . in the name since it's easier that way
  static final Pattern GLSL_IDENTIFIERS = Pattern.compile(
    "(?:#|\\.|\\b)(?!gl_|GL_|OES_|EXT_|WEBGL_|ANGLE_|__)[A-Za-z_][A-Za-z_0-9]*\\b");

  // This assumes that tightenSpaces() has already been run
  static final Pattern VARIABLE_DECLARATIONS = Pattern.compile(
    "(^|[{};])((?:uniform|attribute|varying|const) )?(float|int|bool|mat2|" +
    "mat3|mat4|vec2|vec3|vec4|ivec2|ivec3|ivec4|bvec2|bvec3|bvec4|sampler2D|" +
    "samplerCube) ([^;]+);(?:\\2)?\\3 ([^;]+);");

  static String removeComments(String glsl) {
    return GLSL_COMMENTS.matcher(glsl).replaceAll("");
  }

  static String replace(String source, String pattern, String replace) {
    return Pattern.compile(pattern).matcher(source).replaceAll(replace);
  }

  static String tightenSpaces(String glsl) {
    // Join lines being careful about preprocessor commands
    StringBuilder builder = new StringBuilder();
    for (String line : glsl.split("\n")) {
      builder.append(line).append(
        PREPROCESSOR_COMMAND.matcher(line).find() ? '\n' : ' ');
    }
    glsl = builder.toString();

    // Shrink consecutive spaces into a single space of the same type
    glsl = glsl.trim();
    glsl = replace(glsl, "[ \t]+", " ");
    glsl = replace(glsl, " *[\r\n] *", "\n");
    glsl = replace(glsl, "[\r\n]+", "\n");

    // These symbols are safe to shrink all space on both sides
    glsl = replace(glsl, " *([.,;:?|&^*/=!<>(){}\\[\\]]) *", "$1");

    // Be careful about things like "a - --b"
    glsl = replace(glsl, "\\+ +(?!\\+)", "+");
    glsl = replace(glsl, "\\- +(?!\\-)", "-");
    glsl = replace(glsl, "([^\\+]) +\\+", "$1+");
    glsl = replace(glsl, "([^\\-]) +\\-", "$1-");
    return glsl;
  }

  static String combineAdjacentVariableDeclarations(String glsl) {
    String previous;
    do {
      previous = glsl;
      glsl = VARIABLE_DECLARATIONS.matcher(glsl).replaceFirst("$1$2$3 $4,$5;");
    } while (!glsl.equals(previous));
    return glsl;
  }

  static boolean isReserved(String name) {
    return name.startsWith("#") || name.startsWith(".") ||
           GLSL_KEYWORDS.contains(name);
  }

  static void findNames(String glsl, HashMap<String, Integer> names) {
    Matcher matcher = GLSL_IDENTIFIERS.matcher(glsl);
    while (matcher.find()) {
      String name = matcher.group();
      if (!isReserved(name)) {
        Integer count = names.get(name);
        names.put(name, count == null ? 1 : count + 1);
      }
    }
  }

  static String replaceNames(String glsl, HashMap<String, String> renaming) {
    Matcher matcher = GLSL_IDENTIFIERS.matcher(glsl);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String name = matcher.group();
      matcher.appendReplacement(buffer,
        isReserved(name) ? name : renaming.get(name));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  static String numberToName(int number) {
    String name = "";
    if (number >= 52) {
      name = numberToName(number / 52 - 1);
      number = number % 52;
    }
    name += (char)((number < 26 ? 'a' : 'A' - 26) + number);
    return name;
  }

  void minifyGLSL() {
    HashMap<String, Integer> names = new HashMap<String, Integer>();

    // Scan all string constants
    for (Entry<Node, String> entry : glslStringConstants.entrySet()) {
      Node node = entry.getKey();
      String glsl = entry.getValue();
      glsl = removeComments(glsl);
      glsl = tightenSpaces(glsl);
      glsl = combineAdjacentVariableDeclarations(glsl);
      findNames(glsl, names);
      glslStringConstants.put(node, glsl);
    }

    // Sort identifiers by usage count
    ArrayList<Entry<String, Integer>> sorted = Lists.newArrayList(names.entrySet());
    Collections.sort(sorted, new Comparator<Entry<String, Integer>>() {
      @Override
      public int compare(Entry<String, Integer> a, Entry<String, Integer> b) {
        return b.getValue() - a.getValue();
      }
    });

    // Shorten identifiers
    HashMap<String, String> renaming = new HashMap<String, String>();
    int next = 0;
    for (Entry<String, Integer> entry : sorted) {
      String name;
      do {
        name = numberToName(next++);
      } while (isReserved(name));
      renaming.put(entry.getKey(), name);
    }

    // Replace all string constants
    for (Entry<Node, String> entry : glslStringConstants.entrySet()) {
      Node node = entry.getKey();
      String glsl = entry.getValue();
      glsl = replaceNames(glsl, renaming);
      node.getParent().replaceChild(node, IR.string(glsl));
    }
  }
}
