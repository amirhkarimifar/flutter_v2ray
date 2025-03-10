import NetworkExtension

public class VPNConfigValidator {
    /// 初始化时同步检查（阻塞式，建议在启动时调用）
    class func checkInitialState() {
        let semaphore = DispatchSemaphore(value: 0)
        var isValid = false
        
        NETunnelProviderManager.loadAllFromPreferences { managers, _ in
            defer { semaphore.signal() }
            
            // 1. 检查配置是否存在
            let configExists = managers?.contains {
                ($0.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier == AppConfigs.BUNDLE_IDENTIFIER
            } ?? false
            
            print("configExists \(configExists)")
            
            // 2. 检查活动配置
            let activeConfig = managers?.first { $0.isEnabled }
            print("activeConfig \(activeConfig)")
            let isActiveValid = activeConfig.map {
                ($0.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier == AppConfigs.BUNDLE_IDENTIFIER
            } ?? false
            print("isActiveValid \(isActiveValid)")
            
            isValid = configExists && isActiveValid
            
            if !isValid {
                self.cleanupInvalidState()
            }
        }
        
        // 最多等待2秒获取结果
        _ = semaphore.wait(timeout: .now() + 2.0)
        
        // 3. 更新全局状态
//        AppState.shared.isVPNValid = isValid
    }
    
    /// 清理失效状态
    private class func cleanupInvalidState() {
        // 断开可能的残留连接
//        VPNConnector.shared.disconnect()
//
//        // 清除本地存储的VPN配置
//        KeychainManager.delete(configID: SharedConfig.vpnBundleID)
//
//        // 通知Flutter更新UI
//        EventDispatcher.send(.vpnConfigInvalid)
    }
}
