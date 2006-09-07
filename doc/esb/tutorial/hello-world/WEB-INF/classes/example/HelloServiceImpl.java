package example;

import javax.jws.WebMethod;
import javax.jws.WebService;

@WebService(endpointInterface="example.HelloService")
public class HelloServiceImpl implements HelloService {
  /**
   * Returns "hello, world".
   */
  @WebMethod
  public HelloResult hello()
  {
    return new HelloResult();
  }
}
