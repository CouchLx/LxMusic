module.exports = {
  name: '测试源',
  version: '1.0.0',
  author: 'lxmusic-test',
  description: '仅用于运行时自测的桩插件（不含任何真实音源）',
  api: {
    search: function (keyword, page, limit) {
      return Promise.resolve([
        { id: '1', name: 'Hello ' + keyword, artist: 'Tester', album: 'Test', pic: '', duration: 1000 }
      ])
    },
    debugMd5: function () {
      return lx.utils.crypto.md5('abc')
    },
    getMusicUrl: function (songInfo, quality) {
      return new Promise(function (resolve, reject) {
        lx.request('http://39.96.75.20:8080/api/health', { method: 'get', timeout: 10000 }, function (err, resp) {
          if (err) { reject(err); return }
          resolve('https://example.com/' + resp.statusCode + '.mp3')
        })
      })
    },
    getLyric: function (songInfo) {
      return { lyric: '[00:00.00]测试歌词', tlyric: '' }
    }
  }
}
