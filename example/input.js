/**
 * This tool treats any variable annotated with "@const {GLSL}" as containing
 * GLSL code. All GLSL code is minified together during compilation. This
 * typedef is not included automatically to force you to define it. That way
 * code that uses "@const {GLSL}" will still work with the official Google
 * Closure Compiler.
 *
 * @typedef {string}
 */
var GLSL;

/**
 * @const {GLSL}
 */
var VERTEX_SHADER = '\
  attribute vec2 vertex;\
  \
  void main() {\
    gl_Position = vec4(vertex, 0.0, 1.0);\
  }\
';

/**
 * @const {GLSL}
 */
var FRAGMENT_SHADER = '\
  precision lowp float;\
  uniform vec4 color;\
  \
  void main() {\
    gl_FragColor = color;\
  }\
';

/**
 * Here the uniform is annotated with "@const {GLSL}" so it will be minified
 * along with the shader code.
 *
 * @const {GLSL}
 */
var COLOR_UNIFORM = 'color';

/**
 * @param {WebGLRenderingContext} gl
 */
function setup(gl) {
  function compileShader(type, source) {
    var shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    var info = gl.getShaderInfoLog(shader);
    if (info) throw new Error(info);
    gl.attachShader(program, shader);
  }

  var program = gl.createProgram();
  var buffer = gl.createBuffer();
  compileShader(gl.VERTEX_SHADER, VERTEX_SHADER);
  compileShader(gl.FRAGMENT_SHADER, FRAGMENT_SHADER);
  gl.linkProgram(program);
  gl.useProgram(program);
  gl.uniform4f(gl.getUniformLocation(program, COLOR_UNIFORM), 1, 0, 0, 1);
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, -1, 1, 1, -1, 1, 1]), gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0);
  gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
  gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
}

setup(document.getElementById('canvas').getContext('experimental-webgl'));
