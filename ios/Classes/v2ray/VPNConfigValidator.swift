import Flutter
import NetworkExtension

public class VPNConfigValidator {
    // V2Ray 控制器的单例实例
    private lazy var controller: V2rayController = .shared()
    
    // 单例
    private static var sharedVPNConfigValidator: VPNConfigValidator = .init()
    public class func shared() -> VPNConfigValidator {
        return sharedVPNConfigValidator
    }
 
    /// 初始化时同步检查
    public func checkInitialState(result: @escaping FlutterResult) {
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
            
            // 执行回调
            DispatchQueue.main.async {
                if !isValid {
                    self.controller.stopV2Ray(result: result)
                }
            }
        }
    }
}
