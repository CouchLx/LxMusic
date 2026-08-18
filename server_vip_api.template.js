/**
 * VIP 配置管理服务器端 API（开源模板版）
 *
 * 这是 LXMusic 激活码系统的服务端模板。真实的服务器配置（VIP token、
 * 激活码、管理员 key）不在公开仓库中，请按下方说明自行填写后部署。
 *
 * 部署说明：
 * 1. 将此文件复制为 server_vip_api.js 并添加到你的 Node.js 服务器项目
 * 2. 通过环境变量配置（推荐）：
 *      VIP_TOKEN=你的酷狗VIP账号token
 *      VIP_USERID=你的酷狗VIP账号userid
 *      ADMIN_KEY=你的管理密钥
 * 3. 编辑下方的 ACTIVATE_CODES 生成你的激活码，发给朋友
 * 4. 每月 token 刷新后，只需更新环境变量 VIP_TOKEN，所有已激活设备自动获取新 token
 *
 * 接口：
 * - GET /vip/activate?code=xxx&device_id=xxx  激活设备
 * - GET /vip/config?code=xxx&device_id=xxx    获取最新 token（用于自动更新）
 * - GET /vip/status?admin_key=xxx             管理员：查看激活状态
 * - POST /vip/unbind?admin_key=xxx&code=xxx   管理员：解绑设备
 */

const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

// ==================== 配置区域 ====================

// 当前 VIP 账号（优先从环境变量读取）
const CURRENT_VIP_TOKEN = process.env.VIP_TOKEN || 'your_vip_token_here';
const CURRENT_VIP_USERID = process.env.VIP_USERID || 'your_vip_userid_here';

// 管理员 key（必须通过环境变量设置，禁止写死）
const ADMIN_KEY = process.env.ADMIN_KEY || '';

// 激活码配置（自行填写你的激活码）
const ACTIVATE_CODES = {
  // 格式: '激活码': { maxDevices: 1, expireDate: '2026-12-31', note: '朋友A' }
  'LX001': { maxDevices: 1, expireDate: '2026-12-31', note: '朋友1' },
  'LX002': { maxDevices: 1, expireDate: '2026-12-31', note: '朋友2' },
};

// 数据存储文件路径
const DATA_FILE = path.join(__dirname, 'vip_activations.json');

// ==================== 限流（简单内存版） ====================

const rateLimit = {
  buckets: new Map(),
  max: 10,          // 每分钟最多请求数
  windowMs: 60 * 1000,
  isLimited(ip) {
    const now = Date.now();
    const bucket = this.buckets.get(ip) || { count: 0, resetAt: now + this.windowMs };
    if (now > bucket.resetAt) {
      bucket.count = 0;
      bucket.resetAt = now + this.windowMs;
    }
    bucket.count++;
    this.buckets.set(ip, bucket);
    if (this.buckets.size > 1000) {
      for (const [key, b] of this.buckets) {
        if (now > b.resetAt) this.buckets.delete(key);
      }
    }
    return bucket.count > this.max;
  },
};

function clientIp(req) {
  return req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.ip || 'unknown';
}

// ==================== 数据持久化 ====================

function loadData() {
  try {
    if (fs.existsSync(DATA_FILE)) {
      return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
    }
  } catch (e) {
    console.error('加载数据失败:', e);
  }
  return { activations: {} };
}

function saveData(data) {
  try {
    fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2));
  } catch (e) {
    console.error('保存数据失败:', e);
  }
}

let db = loadData();

// ==================== 工具函数 ====================

function generateCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = 'LX';
  for (let i = 0; i < 4; i++) {
    code += chars[Math.floor(Math.random() * chars.length)];
  }
  return code;
}

function isExpired(expireDate) {
  return new Date(expireDate) < new Date();
}

function getActivation(code) {
  return db.activations[code] || null;
}

function setActivation(code, data) {
  db.activations[code] = data;
  saveData(db);
}

// ==================== API 路由 ====================

const router = express.Router();

/**
 * GET /vip/update/app-release.apk
 * 托管更新 APK（把 APK 放到本文件同目录的 update/ 文件夹即可）
 * 国内用户从自己服务器下载 APK 比 GitHub 快且稳定
 */
router.get('/update/:file', (req, res) => {
  const file = path.basename(req.params.file); // 防路径穿越
  const apkPath = path.join(__dirname, 'update', file);
  if (fs.existsSync(apkPath)) {
    res.download(apkPath);
  } else {
    res.status(404).json({ success: false, message: 'APK 不存在' });
  }
});

/**
 * GET /vip/activate
 * 激活设备
 * Query: code, device_id
 */
router.get('/activate', (req, res) => {
  const { code, device_id } = req.query;

  // 简单限流：防止被刷接口
  const ip = clientIp(req);
  if (rateLimit.isLimited(ip)) {
    console.warn(`[VIP] 限流触发: ip=${ip}`);
    return res.json({ success: false, message: '请求过于频繁，请稍后再试' });
  }

  if (!code || !device_id) {
    return res.json({ success: false, message: '缺少参数: code 或 device_id' });
  }

  const config = ACTIVATE_CODES[code];
  if (!config) {
    return res.json({ success: false, message: '激活码无效' });
  }

  if (isExpired(config.expireDate)) {
    return res.json({ success: false, message: '激活码已过期' });
  }

  const activation = getActivation(code);

  // 如果该激活码已有绑定记录
  if (activation) {
    // 检查是否是同一设备
    if (activation.device_id === device_id) {
      // 同一设备重新激活，允许（可能是清数据了）
      return res.json({
        success: true,
        message: '激活成功（设备已绑定）',
        token: CURRENT_VIP_TOKEN,
        userid: CURRENT_VIP_USERID,
        refresh_interval: 24 * 60 * 60 * 1000, // 1天刷新一次
      });
    }

    // 不同设备，检查是否超过设备上限
    if (activation.device_id && activation.device_id !== device_id) {
      return res.json({
        success: false,
        message: '该激活码已绑定其他设备，无法在此设备上使用',
      });
    }
  }

  // 新激活，绑定设备
  setActivation(code, {
    device_id: device_id,
    activated_at: new Date().toISOString(),
    last_refresh: new Date().toISOString(),
  });

  console.log(`[VIP] 新激活: code=${code}, device=${device_id}, note=${config.note}`);

  return res.json({
    success: true,
    message: '激活成功',
    token: CURRENT_VIP_TOKEN,
    userid: CURRENT_VIP_USERID,
    refresh_interval: 24 * 60 * 60 * 1000,
  });
});

/**
 * GET /vip/config
 * 获取最新 VIP 配置（用于自动更新 token）
 * Query: code, device_id
 */
router.get('/config', (req, res) => {
  const { code, device_id } = req.query;

  if (!code || !device_id) {
    return res.json({ success: false, message: '缺少参数: code 或 device_id' });
  }

  const config = ACTIVATE_CODES[code];
  if (!config) {
    return res.json({ success: false, message: '激活码无效' });
  }

  if (isExpired(config.expireDate)) {
    return res.json({ success: false, message: '激活码已过期' });
  }

  const activation = getActivation(code);

  // 检查是否已激活
  if (!activation || !activation.device_id) {
    return res.json({ success: false, message: '该激活码尚未激活，请先激活' });
  }

  // 检查设备是否匹配
  if (activation.device_id !== device_id) {
    console.log(`[VIP] 设备不匹配: code=${code}, expected=${activation.device_id}, got=${device_id}`);
    return res.json({
      success: false,
      message: '设备未授权，请在原设备上使用或联系管理员',
    });
  }

  // 更新最后刷新时间
  activation.last_refresh = new Date().toISOString();
  setActivation(code, activation);

  return res.json({
    success: true,
    token: CURRENT_VIP_TOKEN,
    userid: CURRENT_VIP_USERID,
  });
});

/**
 * GET /vip/status
 * 查询激活码状态（管理员接口）
 */
router.get('/status', (req, res) => {
  const { admin_key } = req.query;

  // 管理员验证（key 从环境变量 ADMIN_KEY 读取）
  if (!ADMIN_KEY || admin_key !== ADMIN_KEY) {
    return res.status(403).json({ success: false, message: '无权访问' });
  }

  const status = Object.entries(ACTIVATE_CODES).map(([code, config]) => {
    const activation = getActivation(code);
    return {
      code,
      note: config.note,
      maxDevices: config.maxDevices,
      expireDate: config.expireDate,
      isExpired: isExpired(config.expireDate),
      isActivated: !!activation,
      deviceId: activation?.device_id || null,
      activatedAt: activation?.activated_at || null,
      lastRefresh: activation?.last_refresh || null,
    };
  });

  return res.json({ success: true, data: status });
});

/**
 * POST /vip/unbind
 * 解绑设备（管理员接口）
 */
router.post('/unbind', (req, res) => {
  const { admin_key, code } = req.body || req.query;

  if (!ADMIN_KEY || admin_key !== ADMIN_KEY) {
    return res.status(403).json({ success: false, message: '无权访问' });
  }

  if (!code || !db.activations[code]) {
    return res.json({ success: false, message: '激活码未绑定或不存在' });
  }

  delete db.activations[code];
  saveData(db);

  console.log(`[VIP] 解绑: code=${code}`);
  return res.json({ success: true, message: '解绑成功' });
});

/**
 * POST /vip/update-token
 * 更新 VIP token（管理员接口，每月调用一次）
 */
router.post('/update-token', (req, res) => {
  const { admin_key, token, userid } = req.body || req.query;

  if (!ADMIN_KEY || admin_key !== ADMIN_KEY) {
    return res.status(403).json({ success: false, message: '无权访问' });
  }

  if (!token || !userid) {
    return res.json({ success: false, message: '缺少 token 或 userid' });
  }

  // 这里只是返回成功，实际 token 是修改环境变量 VIP_TOKEN
  return res.json({
    success: true,
    message: '请更新环境变量 VIP_TOKEN 和 VIP_USERID 后重启服务',
  });
});

// ==================== 导出 ====================

module.exports = router;

// ==================== 独立运行（测试用） ====================

if (require.main === module) {
  const app = express();
  app.use(express.json());
  app.use('/vip', router);

  const PORT = 3001;
  app.listen(PORT, () => {
    console.log(`VIP API 服务器运行在端口 ${PORT}`);
    console.log('可用接口:');
    console.log('  GET  /vip/activate?code=xxx&device_id=xxx');
    console.log('  GET  /vip/config?code=xxx&device_id=xxx');
    console.log('  GET  /vip/status?admin_key=xxx');
    console.log('  POST /vip/unbind?admin_key=xxx&code=xxx');
    console.log('');
    console.log('生成新激活码:', generateCode());
  });
}
