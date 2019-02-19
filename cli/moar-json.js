var stdin = process.stdin;
var stdout = process.stdout;
var chunks = [];

stdin.resume();
stdin.setEncoding('utf8');

stdin.on('data', function(chunk) {
  chunks.push(chunk);
});

stdin.on('end', function() {
  var input = chunks.join();
  var rExOpenBrace = /\s*\[\s*/gi;
  var rExCloseBrace = /\s*\]\s*/gi;
  var rExColon = /\s*:\s*/g;
  var rExComma = /\s*,\s*/g;
  var output = input.replace(rExOpenBrace, '{\"');
  output = output.replace(rExCloseBrace, '\"}');
  output = output.replace(rExColon, '\":\"');
  output = output.replace(rExComma, '\",\"');
  console.log(output);
});
