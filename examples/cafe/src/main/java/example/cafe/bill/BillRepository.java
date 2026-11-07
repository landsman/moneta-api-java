package example.cafe.bill;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface BillRepository extends ListCrudRepository<Bill, Long> {

    List<Bill> findAllByOrderByIdDesc();
}
