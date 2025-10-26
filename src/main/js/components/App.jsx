import React from "react";

export default function App(props) {
  return (
    <html>
      <head>
        <title>Micronaut React SSR</title>
      </head>
      <body>
        <h1>Hello {props.user || "World"}!</h1>
      </body>
    </html>
  );
}
