(function(dust){dust.register("tpl\/angry_cat-template",body_0);function body_0(chk,ctx){return chk.w("<td>CN: ").f(ctx.get(["name"], false),ctx,"h").w("</td>");}body_0.__dustBody=!0;return body_0}(dust));
(function(dust){dust.register("tpl\/angry_cats-template",body_0);function body_0(chk,ctx){return chk.w("<thead><tr class='header'><th>Name</th></tr></thead><tbody></tbody>");}body_0.__dustBody=!0;return body_0}(dust));
(function(dust){dust.register("tpl\/comment_form",body_0);function body_0(chk,ctx){return chk.w("<!-- generic form --><form id=\"genericForm\"><div><label for=\"uname\">").f(ctx.getPath(false, ["uxph","uname"]),ctx,"h").w("</label><input id=\"uname\" name=\"uname\" type=\"text\"></input></div><div><label for=\"comment\">").f(ctx.getPath(false, ["uxph","comment"]),ctx,"h").w("</label><input id=\"comment\" name=\"comment\" type=\"text\"></input></div><div><input id=\"genericFormSubmit\" type=\"button\" value=\"").f(ctx.getPath(false, ["uxph","formButtonLabel"]),ctx,"h").w("\"></input></div></form>");}body_0.__dustBody=!0;return body_0}(dust));
(function(dust){dust.register("tpl\/header",body_0);function body_0(chk,ctx){return chk.w("<figure><img src=\"").f(ctx.get(["logo"], false),ctx,"h").w("\"></figure><h1>").f(ctx.get(["title"], false),ctx,"h").w("</h1><nav><ul><li><a href=\"#\">home</a></li><li>blog</li><li>contact</li></ul><ul><li><a href=\"#\">login</a></li></ul></nav>");}body_0.__dustBody=!0;return body_0}(dust));
(function(dust){dust.register("tpl\/settings",body_0);function body_0(chk,ctx){return chk.w("<ul class=\"nav nav-pills nav-stacked col-sm-2\"><li class=\"active\"><a href=\"#tab_a\" data-toggle=\"pill\">Tab 1</a></li><li><a href=\"#tab_b\" data-toggle=\"pill\">Tab 2</a></li></ul><div class=\"tab-content col-sm-8\"><div class=\"tab-pane fade in active\" id=\"tab_a\"><h4>Pane A</h4><form><fieldset class=\"form-group\"><label for=\"tab1.entry1\">").f(ctx.getPath(false, ["uxph","entry1"]),ctx,"h").w("</label><input type=\"text\" class=\"form-control\" id=\"tab1.entry1\" placeholder=\"enter text\"></input><small class=\"text-muted\">When you need to enter text</small></fieldset></form></div><div class=\"tab-pane fade\" id=\"tab_b\"><h4>Pane B</h4></div></div>");}body_0.__dustBody=!0;return body_0}(dust));
