const path = require("path");
const webpack = require("webpack");


module.exports = {
  entry: path.resolve(__dirname, 'client.js'),
  devtool: false,
  output: {
    path: path.resolve(__dirname, "../resources/views/static"),
    filename: "client.js"
  },
  resolve: {
    extensions: ['.js', '.jsx'],
  },
  plugins: [
    new webpack.DefinePlugin({ SERVER: false })
  ],
  module: {
    rules: [
      {
        test: /\.(js|jsx)$/,
        exclude: /node_modules/,
        use: { loader: "babel-loader", options: { presets: ["@babel/preset-env", "@babel/preset-react"] } }
      },
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader']
      },
      {
        test: /\.(png|jpe?g|gif|svg)$/i,
        type: 'asset'
      }
    ]
  }
};



/*
module.exports = {
  entry: path.resolve(__dirname, 'client.js'),
  devtool: false,
  output: {
    path: path.resolve(__dirname, "../resources/views/static"),
    filename: "client.js"
  },
  plugins: [
    new webpack.DefinePlugin({
      SERVER: false
    })
  ],
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: {
          loader: "babel-loader",
          options: {
            presets: ["@babel/preset-env", "@babel/preset-react"]
          }
        }
      }
    ]
  }
};
*/