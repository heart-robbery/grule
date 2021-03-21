<style scoped>
.neko {
  z-index: 4;
  width: 100px;
  height: 100px;
  background: #ddd;
  position: fixed;
  cursor: move;
  box-sizing: border-box;
  border: 4px solid #66cc66;
  border-radius: 50%;
  background: url('../img/cat.png') no-repeat center center;
  background-size: 100% 100%;
  overflow: hidden;
}
</style>
<template>
  <div ref="neko" class="neko"></div>
</template>
<script>
module.exports = {
  data: function() {
    return {
    };
  },
  mounted: function () {
    this.init();
  },
  watch: {
  },
  methods: {
    init() {
      let neko = this.$refs.neko;
      let nekoW = neko.offsetWidth;
      let nekoH = neko.offsetHeight;
      let cuntW = 0;
      let cuntH = 0;
      neko.style.left = parseInt(Math.random() * (document.body.offsetWidth - nekoW)) + 'px';
      neko.style.top = parseInt(Math.random() * (document.body.offsetHeight - nekoH)) + 'px';
      neko.onmousedown = function (e) {
        let nekoL = e.clientX - neko.offsetLeft;
        let nekoT = e.clientY - neko.offsetTop;
        document.onmousemove = function (e) {
          cuntW = 0;
          cuntH = 0;
          neko.direction = '';
          neko.style.transition = '';
          neko.style.left = (e.clientX - nekoL) + 'px';
          neko.style.top = (e.clientY - nekoT) + 'px';
          if (e.clientX - nekoL < 5) {
            neko.direction = 'left';
          }
          if (e.clientY - nekoT < 5) {
            neko.direction = 'top';
          }
          if (e.clientX - nekoL > document.body.offsetWidth - nekoW - 5) {
            neko.direction = 'right';
          }
          if (e.clientY - nekoT > document.body.offsetHeight - nekoH - 5) {
            neko.direction = 'bottom';
          }
          move(neko, 0, 0);
        }
      }
      function move(obj, w, h) {
        if (obj.direction === 'left') {
          obj.style.left = 0 - w + 'px';
        } else if (obj.direction === 'right') {
          obj.style.left = document.body.offsetWidth - nekoW + w + 'px';
        }
        if (obj.direction === 'top') {
          obj.style.top = 0 - h + 'px';
        } else if (obj.direction === 'bottom') {
          obj.style.top = document.body.offsetHeight - nekoH + h + 'px';
        }
      }
      function rate(obj, a) {
        obj.style.transform = ' rotate(' + a + ')'
      }
      function action(obj) {
        let dir = obj.direction;
        switch (dir) {
          case 'left':
            rate(obj, '90deg');
            break;
          case 'right':
            rate(obj, '-90deg');
            break;
          case 'top':
            rate(obj, '-180deg');
            break;
          default:
            rate(obj, '-0');
            break;
        }
      }
      neko.onmouseover = function () {
        move(this, 0, 0);
        rate(this, 0)
      }
      neko.onmouseout = function () {
        move(this, nekoW / 2, nekoH / 2);
        action(this);
      }
      neko.onmouseup = function () {
        document.onmousemove = null;
        this.style.transition = '.5s';
        move(this, nekoW / 2, nekoH / 2);
        action(this);
      }
      window.onresize = function () {
        let bodyH = document.body.offsetHeight;
        let nekoT = neko.offsetTop;
        let bodyW = document.body.offsetWidth;
        let nekoL = neko.offsetLeft;

        if (nekoT + nekoH > bodyH) {
          neko.style.top = bodyH - nekoH + 'px';
          cuntH++;
        }
        if (bodyH > nekoT && cuntH > 0) {
          neko.style.top = bodyH - nekoH + 'px';
        }
        if (nekoL + nekoW > bodyW) {
          neko.style.left = bodyW - nekoW + 'px';
          cuntW++;
        }
        if (bodyW > nekoL && cuntW > 0) {
          neko.style.left = bodyW - nekoW + 'px';
        }
        move(neko, nekoW / 2, nekoH / 2);
      }
    }
  },
};
</script>