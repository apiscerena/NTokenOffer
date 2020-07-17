package com.nest.ib.vo;


import com.nest.ib.contract.ERC20;
import com.nest.ib.contract.MappingContract;
import com.nest.ib.contract.NTokenMapping;
import com.nest.ib.service.MiningService;
import com.nest.ib.service.serviceImpl.MiningServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;
import java.math.BigInteger;

@Component
public class TheFirstUpdateTask implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(MiningServiceImpl.class);

    @Value("${mapping.contract.address}")
    private String MAPPING_CONTRACT_ADDRESS;
    @Value("${eth.node}")
    private String ETH_NODE;
    @Value("${private.key}")
    private String USER_PRIVATE_KEY;
    @Value("${erc20.token.address}")
    private String ERC20_TOKEN_ADDRESS;

    @Autowired
    private MiningService miningService;

    @Override
    public void run(ApplicationArguments args) {
        Web3j web3j = Web3j.build(new HttpService(ETH_NODE));
        Credentials credentials = Credentials.create(USER_PRIVATE_KEY);
        /**
        *   更新报价工厂合约地址
        */
        MappingContract mappingContract = MappingContract.load(MAPPING_CONTRACT_ADDRESS,web3j,credentials,Contract.GAS_PRICE,Contract.GAS_LIMIT);

        String offerFactoryContractAddress;
        String offerPriceContractAddress;
        String nTokenMappingContractAddress;
        String nTokenContactAddress;
        BigInteger erc20Decimal;
        try {
            offerFactoryContractAddress = mappingContract.checkAddress("nest.nToken.offerMain").sendAsync().get();
            LOG.info(offerFactoryContractAddress);
            offerPriceContractAddress = mappingContract.checkAddress("nest.v3.offerPrice").sendAsync().get();
            LOG.info(offerPriceContractAddress);
            nTokenMappingContractAddress = mappingContract.checkAddress("nest.nToken.tokenMapping").sendAsync().get();
            LOG.info(nTokenMappingContractAddress);
            nTokenContactAddress = NTokenMapping.load(nTokenMappingContractAddress, web3j, credentials, Contract.GAS_PRICE, Contract.GAS_LIMIT).checkTokenMapping(ERC20_TOKEN_ADDRESS).sendAsync().get();
            LOG.info(nTokenContactAddress);
            erc20Decimal = ERC20.load(ERC20_TOKEN_ADDRESS, web3j, credentials, Contract.GAS_PRICE, Contract.GAS_LIMIT).decimals().sendAsync().get();
            LOG.info(String.valueOf(erc20Decimal));
        } catch (Exception e) {
            LOG.error("通过映射合约获取其他合约地址失败");
            e.printStackTrace();
            return;
        }
        // 如果调用映射合约失败，会返回0x0000000000000000000000000000000000000000，避免此种情况
        if(!offerFactoryContractAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000")
                && !offerPriceContractAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000")
                && !nTokenContactAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000")
                && erc20Decimal.compareTo(new BigInteger("0")) > 0){
            miningService.updateOfferFactoryContractAddress(offerFactoryContractAddress);
            miningService.updateOfferPriceContractAddress(offerPriceContractAddress);
            miningService.updateNTokenContractAddress(nTokenContactAddress);
            miningService.updateErc20Decimal(erc20Decimal);
            //对报价工厂合约进行USDT授权
            try {
                approveToOfferFactoryContract(web3j,offerFactoryContractAddress);
            } catch (Exception e) {
                LOG.error("对报价工厂合约进行HT授权失败");
                e.printStackTrace();
                return;
            }
        }
    }
    /**
     *  检测是否进行了一次性授权,如果没有,即进行一次性授权
     */
    private void approveToOfferFactoryContract(Web3j web3j,String offerFactoryContractAddress) throws Exception {
        Credentials credentials = Credentials.create(USER_PRIVATE_KEY);
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(new BigInteger("2"));
        ERC20 erc20 = ERC20.load(ERC20_TOKEN_ADDRESS, web3j, credentials, gasPrice, new BigInteger("200000"));
        BigInteger approveValue = erc20.allowance(credentials.getAddress(), offerFactoryContractAddress).sendAsync().get();
        LOG.info("已经授权金额为：" + approveValue);
        if(approveValue.compareTo(new BigInteger("100000000000000")) < 0){
            String transactionHash = erc20.approve(offerFactoryContractAddress, new BigInteger("99999999999999999999")).sendAsync().get().getTransactionHash();
            LOG.info("一次性授权hash：" + transactionHash);
        }
    }
}
