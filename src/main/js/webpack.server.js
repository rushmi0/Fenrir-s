const path = require('path');
const webpack = require('webpack');

module.exports = {
  entry: ['web-streams-polyfill/dist/polyfill', path.resolve(__dirname, 'server.js')],
  output: {
    path: path.resolve(__dirname, '../resources/views/'),
    filename: 'ssr-components.mjs',
    module: true,
    library: { type: 'module' },
    globalObject: 'globalThis'
  },
  resolve: {
    extensions: ['.js', '.jsx'],
  },
  devtool: false,
  experiments: { outputModule: true },
  plugins: [
    new webpack.ProvidePlugin({
      TextEncoder: ['text-encoding', 'TextEncoder'],
      TextDecoder: ['text-encoding', 'TextDecoder']
    }),
    new webpack.DefinePlugin({
      SERVER: true,
    })
  ],
  module: {
    rules: [
      {
        test: /\.(js|jsx)$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: { presets: ['@babel/preset-env', '@babel/preset-react'] }
        }
      },
      {
        test: /\.css$/,
        use: ['css-loader'] // สำหรับ SSR ไม่ต้องใช้ style-loader
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
  entry: ['web-streams-polyfill/dist/polyfill', path.resolve(__dirname, 'server.js')],
  output: {
    path: path.resolve(__dirname, '../resources/views/'),
    filename: 'ssr-components.mjs',
    module: true,
    library: { type: 'module' },
    globalObject: 'globalThis'
  },
  devtool: false,
  experiments: { outputModule: true },
  plugins: [
    new webpack.ProvidePlugin({
      TextEncoder: ['text-encoding', 'TextEncoder'],
      TextDecoder: ['text-encoding', 'TextDecoder']
    }),
    new webpack.DefinePlugin({
      SERVER: true,
    })
  ],
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: ['@babel/preset-env', '@babel/preset-react']
          }
        }
      }
    ]
  }
};
*/

